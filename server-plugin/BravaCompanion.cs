// BravaCompanion - server side of the Brava RCON Android app.
// Sends phone alerts through ntfy, runs scheduled commands, tags the server name after a wipe,
// keeps F7 reports and a player history, and lets the app edit kits and the Carbon wipe calendar.
// Install: copy this file into carbon/plugins or oxide/plugins.
// License: GPL-3.0

using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.RegularExpressions;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Oxide.Core;
using Oxide.Core.Libraries;

namespace Oxide.Plugins
{
    [Info("BravaCompanion", "Brava RCON", "1.3.0")]
    [Description("Phone alerts (ntfy) and scheduled commands for the Brava RCON app.")]
    public class BravaCompanion : RustPlugin
    {
        private class ScheduleItem
        {
            public string Time = "";
            public int EveryMinutes = 0;
            public string Command = "";
        }

        private class Settings
        {
            public string NtfyServer = "https://ntfy.sh";
            public string Topic = "";
            // Defaults are added in LoadDefaultConfig: a list filled here would be appended to on every load.
            public List<string> Keywords = new List<string>();
            public bool NotifyJoins = false;
            public bool NotifyWipe = true;
            public bool NotifyStart = true;
            public double UtcOffsetHours = 0;
            public List<ScheduleItem> Schedules = new List<ScheduleItem>();
            // Server name used while the map is fresh. {date} becomes the wipe day (dd/MM). Empty turns it off.
            public string FreshHostname = "";
            public double FreshHours = 48;
            // "es" or "en": language of the phone alerts.
            public string Language = "en";
            public bool NotifyReports = true;
        }

        private class Report
        {
            public long Time;
            public string ReporterId = "";
            public string ReporterName = "";
            public string TargetId = "";
            public string TargetName = "";
            public string Type = "";
            public string Subject = "";
            public string Message = "";
        }

        private class Seen
        {
            public string Id = "";
            public string Name = "";
            public long First;
            public long Last;
            public int Visits;
        }

        private class StoredData
        {
            public List<Report> Reports = new List<Report>();
            public Dictionary<string, Seen> Players = new Dictionary<string, Seen>();
        }

        private StoredData data = new StoredData();
        private bool dataDirty;
        private string mapImage;
        private int mapWidth, mapHeight, mapMargin;
        private const int Chunk = 60000;

        private Settings settings = new Settings();
        private readonly Dictionary<int, string> lastDailyRun = new Dictionary<int, string>();
        private readonly Dictionary<int, DateTime> nextRepeat = new Dictionary<int, DateTime>();
        private bool wiped;
        private string baseHostname = "";

        protected override void LoadDefaultConfig()
        {
            settings = new Settings();
            settings.Keywords = new List<string> { "admin", "hacker", "cheater", "hack", "tramposo" };
        }

        protected override void LoadConfig()
        {
            base.LoadConfig();
            try
            {
                settings = Config.ReadObject<Settings>() ?? new Settings();
            }
            catch
            {
                PrintWarning("Config file is invalid, using defaults.");
                settings = new Settings();
            }
            if (settings.Keywords == null) settings.Keywords = new List<string>();
            if (settings.Schedules == null) settings.Schedules = new List<ScheduleItem>();
            SaveConfig();
        }

        protected override void SaveConfig()
        {
            Config.WriteObject(settings, true);
        }

        private void OnNewSave(string filename)
        {
            wiped = true;
        }

        private void OnServerInitialized(bool initial)
        {
            baseHostname = ConVar.Server.hostname;
            ApplyHostname();
            // Drawing the terrain freezes the server for several seconds, so do it once at start-up
            // (before players join) and keep it on disk for the rest of the wipe.
            timer.Once(1f, () => PrepareMap(false));
            timer.Every(20f, Tick);
            if (wiped && settings.NotifyWipe) Push(T("Wipe finished", "Wipe terminado"), ConVar.Server.hostname + T(" is up with a new map.", " ya está en línea con mapa nuevo."));
            else if (initial && settings.NotifyStart) Push(T("Server started", "Servidor encendido"), ConVar.Server.hostname + T(" is online.", " está en línea."));
            wiped = false;
        }

        private void Unload()
        {
            // Put the normal name back so a reload does not mistake the tagged name for the normal one.
            if (!string.IsNullOrEmpty(baseHostname)) ConVar.Server.hostname = baseHostname;
            foreach (BasePlayer player in BasePlayer.activePlayerList) Touch(player, false);
            SaveData();
        }

        // ---- fresh-wipe server name ----

        private DateTime WipeTimeUtc()
        {
            return SaveRestore.SaveCreatedTime;
        }

        private double HoursSinceWipe()
        {
            return (DateTime.UtcNow - WipeTimeUtc()).TotalHours;
        }

        private bool IsFresh()
        {
            double hours = HoursSinceWipe();
            return !string.IsNullOrWhiteSpace(settings.FreshHostname) && settings.FreshHours > 0 && hours >= 0 && hours < settings.FreshHours;
        }

        private void ApplyHostname()
        {
            if (string.IsNullOrEmpty(baseHostname)) return;
            string wanted = baseHostname;
            if (IsFresh())
            {
                string date = WipeTimeUtc().AddHours(settings.UtcOffsetHours).ToString("dd/MM", System.Globalization.CultureInfo.InvariantCulture);
                wanted = settings.FreshHostname.Replace("{date}", date);
            }
            if (ConVar.Server.hostname == wanted) return;
            ConVar.Server.hostname = wanted;
            Puts("Server name set to: " + wanted);
        }

        // Runs a full command line ("name arg1 arg2") the way the server console does. Carbon's
        // Server.Command treats the whole line as a command name, and passing args separately
        // quotes them, which Carbon's own commands do not expect.
        private string RunCommand(string line)
        {
            return ConsoleSystem.Run(ConsoleSystem.Option.Server.Quiet(), line) ?? "";
        }

        // Carbon's c.reloadmodule does not answer commands sent from plugin code, so call
        // Carbon.Base.BaseModule.FindModule(name).Reload() directly. Oxide has no modules.
        private bool ReloadCarbonModule(string name)
        {
            try
            {
                foreach (Assembly asm in AppDomain.CurrentDomain.GetAssemblies())
                {
                    Type baseModule = asm.GetType("Carbon.Base.BaseModule", false);
                    if (baseModule == null) continue;
                    MethodInfo find = baseModule.GetMethod("FindModule", BindingFlags.Public | BindingFlags.Static);
                    object module = find?.Invoke(null, new object[] { name });
                    if (module == null) return false;
                    MethodInfo reload = module.GetType().GetMethod("Reload", BindingFlags.Public | BindingFlags.Instance, null, Type.EmptyTypes, null);
                    if (reload == null) return false;
                    reload.Invoke(module, null);
                    return true;
                }
            }
            catch (Exception e)
            {
                PrintWarning("Could not reload module " + name + ": " + e.GetBaseException().Message);
            }
            return false;
        }

        private string T(string en, string es)
        {
            return settings.Language == "es" ? es : en;
        }

        private static long Now()
        {
            return DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        }

        private void Init()
        {
            try
            {
                data = Interface.Oxide.DataFileSystem.ReadObject<StoredData>(Name) ?? new StoredData();
            }
            catch
            {
                data = new StoredData();
            }
            if (data.Reports == null) data.Reports = new List<Report>();
            if (data.Players == null) data.Players = new Dictionary<string, Seen>();
        }

        private void OnServerSave()
        {
            SaveData();
        }

        private void SaveData()
        {
            if (!dataDirty) return;
            dataDirty = false;
            Interface.Oxide.DataFileSystem.WriteObject(Name, data);
        }

        private void Touch(BasePlayer player, bool joining)
        {
            if (player == null || player.UserIDString.Length != 17 || !player.UserIDString.StartsWith("7656")) return;
            string id = player.UserIDString;
            Seen seen;
            if (!data.Players.TryGetValue(id, out seen))
            {
                seen = new Seen { Id = id, First = Now() };
                data.Players[id] = seen;
            }
            seen.Name = player.displayName;
            seen.Last = Now();
            if (joining) seen.Visits++;
            dataDirty = true;
            if (data.Players.Count > 2000)
            {
                foreach (var old in data.Players.Values.OrderBy(p => p.Last).Take(data.Players.Count - 2000).ToList()) data.Players.Remove(old.Id);
            }
        }

        private void OnPlayerDisconnected(BasePlayer player, string reason)
        {
            Touch(player, false);
        }

        private void OnPlayerReported(BasePlayer reporter, string targetName, string targetId, string subject, string message, string type)
        {
            if (reporter == null) return;
            data.Reports.Insert(0, new Report
            {
                Time = Now(),
                ReporterId = reporter.UserIDString,
                ReporterName = reporter.displayName,
                TargetId = targetId ?? "",
                TargetName = targetName ?? "",
                Type = type ?? "",
                Subject = subject ?? "",
                Message = message ?? ""
            });
            if (data.Reports.Count > 200) data.Reports.RemoveRange(200, data.Reports.Count - 200);
            dataDirty = true;
            SaveData();
            if (settings.NotifyReports)
            {
                Push(T("F7 report", "Reporte F7") + ": " + targetName, reporter.displayName + " (" + type + "): " + subject + (string.IsNullOrEmpty(message) ? "" : " - " + message));
            }
        }

        private void OnPlayerConnected(BasePlayer player)
        {
            Touch(player, true);
            if (player == null || !settings.NotifyJoins) return;
            Push(T("Player joined", "Entró un jugador"), player.displayName + " (" + BasePlayer.activePlayerList.Count + T(" online)", " conectados)"));
        }

        private void OnPlayerChat(BasePlayer player, string message, ConVar.Chat.ChatChannel channel)
        {
            if (player == null || string.IsNullOrEmpty(message) || settings.Keywords.Count == 0) return;
            string lower = message.ToLowerInvariant();
            foreach (string word in settings.Keywords)
            {
                if (string.IsNullOrWhiteSpace(word)) continue;
                if (lower.Contains(word.Trim().ToLowerInvariant()))
                {
                    Push(T("Chat alert", "Alerta del chat"), player.displayName + ": " + message);
                    return;
                }
            }
        }

        // ---- ntfy ----

        private void Push(string title, string body)
        {
            if (string.IsNullOrWhiteSpace(settings.Topic)) return;
            string url = settings.NtfyServer.TrimEnd('/') + "/" + settings.Topic.Trim();
            var headers = new Dictionary<string, string>
            {
                { "Title", title },
                { "Content-Type", "text/plain; charset=utf-8" }
            };
            webrequest.Enqueue(url, body, (code, response) =>
            {
                if (code < 200 || code >= 300) PrintWarning("ntfy answered HTTP " + code);
            }, this, RequestMethod.POST, headers, 10f);
        }

        // ---- scheduler ----

        private DateTime LocalNow()
        {
            return DateTime.UtcNow.AddHours(settings.UtcOffsetHours);
        }

        private void Tick()
        {
            ApplyHostname();
            DateTime now = LocalNow();
            string hhmm = now.ToString("HH:mm");
            string today = now.ToString("yyyy-MM-dd");
            for (int i = 0; i < settings.Schedules.Count; i++)
            {
                ScheduleItem item = settings.Schedules[i];
                if (string.IsNullOrWhiteSpace(item.Command)) continue;
                if (item.EveryMinutes > 0)
                {
                    DateTime due;
                    if (!nextRepeat.TryGetValue(i, out due))
                    {
                        nextRepeat[i] = now.AddMinutes(item.EveryMinutes);
                        continue;
                    }
                    if (now >= due)
                    {
                        nextRepeat[i] = now.AddMinutes(item.EveryMinutes);
                        RunCommand(item.Command);
                    }
                }
                else if (item.Time == hhmm)
                {
                    string last;
                    if (lastDailyRun.TryGetValue(i, out last) && last == today) continue;
                    lastDailyRun[i] = today;
                    RunCommand(item.Command);
                }
            }
        }

        private void ResetScheduleState()
        {
            lastDailyRun.Clear();
            nextRepeat.Clear();
        }

        // ---- console commands (used by the app over RCON) ----

        private bool Allowed(ConsoleSystem.Arg arg)
        {
            if (arg.IsAdmin) return true;
            arg.ReplyWith("No permission.");
            return false;
        }

        private static bool ParseBool(string value, bool fallback)
        {
            bool parsed;
            return bool.TryParse(value, out parsed) ? parsed : fallback;
        }

        [ConsoleCommand("brava.status")]
        private void CmdStatus(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            arg.ReplyWith(JsonConvert.SerializeObject(settings));
        }

        [ConsoleCommand("brava.wipetag")]
        private void CmdWipeTag(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.FreshHostname = arg.GetString(0, "").Trim();
            double hours;
            if (double.TryParse(arg.GetString(1, ""), System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out hours) && hours >= 0 && hours <= 720)
            {
                settings.FreshHours = hours;
            }
            SaveConfig();
            ApplyHostname();
            arg.ReplyWith("Wipe tag saved. Map age: " + HoursSinceWipe().ToString("0.0", System.Globalization.CultureInfo.InvariantCulture) + " h. Name now: " + ConVar.Server.hostname);
        }

        [ConsoleCommand("brava.topic")]
        private void CmdTopic(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.Topic = arg.GetString(0, "").Trim();
            SaveConfig();
            arg.ReplyWith("Topic saved.");
        }

        [ConsoleCommand("brava.server")]
        private void CmdServer(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            string value = arg.GetString(0, "").Trim();
            if (!value.StartsWith("http")) { arg.ReplyWith("Use a full address, for example https://ntfy.sh"); return; }
            settings.NtfyServer = value;
            SaveConfig();
            arg.ReplyWith("ntfy server saved.");
        }

        [ConsoleCommand("brava.keywords")]
        private void CmdKeywords(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.Keywords = new List<string>();
            foreach (string word in arg.GetString(0, "").Split(','))
            {
                if (!string.IsNullOrWhiteSpace(word)) settings.Keywords.Add(word.Trim());
            }
            SaveConfig();
            arg.ReplyWith("Keywords saved: " + settings.Keywords.Count);
        }

        [ConsoleCommand("brava.joins")]
        private void CmdJoins(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.NotifyJoins = ParseBool(arg.GetString(0, ""), settings.NotifyJoins);
            SaveConfig();
            arg.ReplyWith("Join alerts: " + settings.NotifyJoins);
        }

        [ConsoleCommand("brava.wipe")]
        private void CmdWipe(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.NotifyWipe = ParseBool(arg.GetString(0, ""), settings.NotifyWipe);
            SaveConfig();
            arg.ReplyWith("Wipe alerts: " + settings.NotifyWipe);
        }

        [ConsoleCommand("brava.start")]
        private void CmdStart(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.NotifyStart = ParseBool(arg.GetString(0, ""), settings.NotifyStart);
            SaveConfig();
            arg.ReplyWith("Start alerts: " + settings.NotifyStart);
        }

        [ConsoleCommand("brava.offset")]
        private void CmdOffset(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            double hours;
            if (!double.TryParse(arg.GetString(0, ""), System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out hours) || hours < -12 || hours > 14)
            {
                arg.ReplyWith("Use a number of hours between -12 and 14.");
                return;
            }
            settings.UtcOffsetHours = hours;
            ResetScheduleState();
            SaveConfig();
            arg.ReplyWith("Clock offset saved. Server time is now " + LocalNow().ToString("HH:mm"));
        }

        [ConsoleCommand("brava.schedule.add")]
        private void CmdScheduleAdd(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            string when = arg.GetString(0, "").Trim();
            string command = arg.GetString(1, "").Trim();
            if (command.Length == 0) { arg.ReplyWith("Usage: brava.schedule.add <HH:mm | every:minutes> \"command\""); return; }
            var item = new ScheduleItem { Command = command };
            if (when.StartsWith("every:"))
            {
                int minutes;
                if (!int.TryParse(when.Substring(6), out minutes) || minutes < 1) { arg.ReplyWith("Minutes must be 1 or more."); return; }
                item.EveryMinutes = minutes;
            }
            else
            {
                DateTime parsed;
                if (!DateTime.TryParseExact(when, "HH:mm", System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out parsed))
                {
                    arg.ReplyWith("Time must look like 13:55.");
                    return;
                }
                item.Time = when;
            }
            settings.Schedules.Add(item);
            ResetScheduleState();
            SaveConfig();
            arg.ReplyWith("Scheduled.");
        }

        [ConsoleCommand("brava.schedule.remove")]
        private void CmdScheduleRemove(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            int index = arg.GetInt(0, -1);
            if (index < 0 || index >= settings.Schedules.Count) { arg.ReplyWith("No schedule with that number."); return; }
            settings.Schedules.RemoveAt(index);
            ResetScheduleState();
            SaveConfig();
            arg.ReplyWith("Removed.");
        }

        // ---- /raid: where the Raidable Bases events are ----

        private static readonly string[] Compass = { "norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste" };

        [ChatCommand("raid")]
        private void CmdRaid(BasePlayer player, string command, string[] args)
        {
            if (player == null) return;
            // Raidable Bases puts a vending-style marker on every open base; read those.
            var bases = BaseNetworkable.serverEntities.OfType<VendingMachineMapMarker>()
                .Where(m => m != null && !m.IsDestroyed && m.markerShopName != null &&
                            (m.markerShopName.IndexOf("raideable", StringComparison.OrdinalIgnoreCase) >= 0 ||
                             m.markerShopName.IndexOf("raidable", StringComparison.OrdinalIgnoreCase) >= 0))
                .Select(m => m.transform.position)
                .OrderBy(p => Vector3Ex.Distance2D(p, player.transform.position))
                .ToList();
            if (bases.Count == 0)
            {
                SendReply(player, "<color=#ffb35c>[RAID]</color> Ahorita no hay bases raideables. Aparecen solas cuando hay gente conectada.");
                return;
            }
            var sb = new System.Text.StringBuilder();
            sb.Append("<color=#ffb35c>[RAID]</color> Bases raideables (" + bases.Count + "), de la más cercana a la más lejana:");
            foreach (var pos in bases)
            {
                Vector3 delta = pos - player.transform.position;
                float angle = (Mathf.Atan2(delta.x, delta.z) * Mathf.Rad2Deg + 360f) % 360f;
                string dir = Compass[(int)Mathf.Round(angle / 45f) % 8];
                sb.Append("\n  <color=#FFFF00>" + MapHelper.PositionToString(pos) + "</color> · " +
                          Mathf.RoundToInt(Vector3Ex.Distance2D(pos, player.transform.position)) + " m al " + dir);
            }
            sb.Append("\nTambién salen en el mapa (G) como \"Base raideable\".");
            SendReply(player, sb.ToString());
        }

        [ConsoleCommand("brava.language")]
        private void CmdLanguage(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.Language = arg.GetString(0, "en").Trim().ToLowerInvariant() == "es" ? "es" : "en";
            SaveConfig();
            arg.ReplyWith("Alert language: " + settings.Language);
        }

        [ConsoleCommand("brava.reportalerts")]
        private void CmdReportAlerts(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            settings.NotifyReports = ParseBool(arg.GetString(0, ""), settings.NotifyReports);
            SaveConfig();
            arg.ReplyWith("Report alerts: " + settings.NotifyReports);
        }

        // ---- F7 reports, player history, combat log ----

        [ConsoleCommand("brava.reports")]
        private void CmdReports(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            arg.ReplyWith(JsonConvert.SerializeObject(data.Reports.Take(Math.Max(1, arg.GetInt(0, 100))).ToList()));
        }

        [ConsoleCommand("brava.reports.clear")]
        private void CmdReportsClear(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            data.Reports.Clear();
            dataDirty = true;
            SaveData();
            arg.ReplyWith("Reports cleared.");
        }

        [ConsoleCommand("brava.history")]
        private void CmdHistory(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            var online = new HashSet<string>(BasePlayer.activePlayerList.Select(p => p.UserIDString));
            var list = data.Players.Values.OrderByDescending(p => online.Contains(p.Id)).ThenByDescending(p => p.Last)
                .Take(Math.Max(1, arg.GetInt(0, 300)))
                .Select(p => new { p.Id, p.Name, p.First, p.Last, p.Visits, Online = online.Contains(p.Id) })
                .ToList();
            arg.ReplyWith(JsonConvert.SerializeObject(list));
        }

        [ConsoleCommand("brava.combatlog")]
        private void CmdCombatLog(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            ulong id;
            if (!ulong.TryParse(arg.GetString(0, ""), out id)) { arg.ReplyWith("Usage: brava.combatlog <steamid> [lines]"); return; }
            BasePlayer player = BasePlayer.FindAwakeOrSleeping(id.ToString());
            if (player == null) { arg.ReplyWith(T("That player is not on the server (not connected or sleeping).", "Ese jugador no está en el servidor (ni conectado ni dormido).")); return; }
            int lines = Math.Max(5, Math.Min(100, arg.GetInt(1, 30)));
            string log = CombatText(player, lines);
            arg.ReplyWith(string.IsNullOrWhiteSpace(log) ? T("No combat recorded.", "No hay combate registrado.") : log);
        }

        private string CombatText(BasePlayer player, int lines)
        {
            object combat = player.stats?.combat;
            if (combat == null) return "";
            MethodInfo get = combat.GetType().GetMethods(BindingFlags.Public | BindingFlags.Instance)
                .Where(m => m.Name == "Get" && m.ReturnType == typeof(string))
                .OrderBy(m => m.GetParameters().Length).FirstOrDefault();
            if (get == null) return "";
            ParameterInfo[] ps = get.GetParameters();
            var args = new object[ps.Length];
            for (int i = 0; i < ps.Length; i++)
            {
                ParameterInfo p = ps[i];
                if (i == 0 && p.ParameterType == typeof(int)) args[i] = lines;
                else if (p.ParameterType == typeof(bool) && p.Name.ToLowerInvariant().Contains("admin")) args[i] = true;
                else if (p.HasDefaultValue) args[i] = p.DefaultValue ?? (p.ParameterType.IsValueType ? Activator.CreateInstance(p.ParameterType) : null);
                else args[i] = p.ParameterType.IsValueType ? Activator.CreateInstance(p.ParameterType) : null;
            }
            return get.Invoke(combat, args) as string ?? "";
        }

        // ---- terrain image for the app's player map ----

        private class MapCache
        {
            public uint Seed;
            public int Size;
            public int Width;
            public int Height;
            public int Margin;
            public string Data;
        }

        private string MapCacheFile()
        {
            return Path.Combine(Interface.Oxide.DataDirectory, "BravaCompanion_map.json");
        }

        private void PrepareMap(bool force)
        {
            try
            {
                string path = MapCacheFile();
                if (File.Exists(path))
                {
                    var cache = JsonConvert.DeserializeObject<MapCache>(File.ReadAllText(path));
                    if (cache != null && cache.Seed == World.Seed && cache.Size == World.Size && !string.IsNullOrEmpty(cache.Data))
                    {
                        mapImage = cache.Data;
                        mapWidth = cache.Width;
                        mapHeight = cache.Height;
                        mapMargin = cache.Margin;
                        return;
                    }
                }
                if (!force && BasePlayer.activePlayerList.Count > 0) return;
                RenderMap();
                File.WriteAllText(path, JsonConvert.SerializeObject(new MapCache
                {
                    Seed = World.Seed, Size = (int)World.Size, Width = mapWidth, Height = mapHeight, Margin = mapMargin, Data = mapImage
                }));
                Puts("Terrain image ready for the app (" + mapWidth + " px).");
            }
            catch (Exception e)
            {
                PrintWarning("Could not prepare the terrain image: " + e.GetBaseException().Message);
            }
        }

        [ConsoleCommand("brava.mapimage")]
        private void CmdMapImage(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            if (mapImage == null)
            {
                // Never draw it while people play: it freezes the server long enough to time them out.
                bool force = arg.GetString(1, "") == "force";
                if (BasePlayer.activePlayerList.Count > 0 && !force)
                {
                    arg.ReplyWith("{\"Error\":\"busy\"}");
                    return;
                }
                PrepareMap(force);
                if (mapImage == null)
                {
                    arg.ReplyWith("{\"Error\":\"Could not draw the terrain image\"}");
                    return;
                }
            }
            int chunks = (mapImage.Length + Chunk - 1) / Chunk;
            int index = Math.Max(0, Math.Min(chunks - 1, arg.GetInt(0, 0)));
            arg.ReplyWith(JsonConvert.SerializeObject(new
            {
                Seed = World.Seed,
                Size = World.Size,
                Width = mapWidth,
                Height = mapHeight,
                Margin = mapMargin,
                Chunks = chunks,
                Index = index,
                Data = mapImage.Substring(index * Chunk, Math.Min(Chunk, mapImage.Length - index * Chunk))
            }));
        }

        private void RenderMap()
        {
            Type renderer = typeof(BasePlayer).Assembly.GetType("MapImageRenderer");
            if (renderer == null) throw new Exception("MapImageRenderer not found");
            MethodInfo render = renderer.GetMethods(BindingFlags.Public | BindingFlags.Static)
                .Where(m => m.Name == "Render" && m.ReturnType == typeof(byte[]))
                .OrderByDescending(m => m.GetParameters().Length).FirstOrDefault();
            if (render == null) throw new Exception("Render method not found");
            ParameterInfo[] ps = render.GetParameters();
            var args = new object[ps.Length];
            int widthAt = -1, heightAt = -1;
            int margin = 500;
            for (int i = 0; i < ps.Length; i++)
            {
                ParameterInfo p = ps[i];
                string n = p.Name.ToLowerInvariant();
                if (p.IsOut)
                {
                    if (n.Contains("width")) widthAt = i;
                    else if (n.Contains("height")) heightAt = i;
                    args[i] = null;
                }
                else if (n == "scale") args[i] = 0.3f;
                else if (n == "lossy") args[i] = true;
                else if (n.Contains("transparent")) args[i] = false;
                else if (n.Contains("margin")) args[i] = margin;
                else if (p.HasDefaultValue) args[i] = p.DefaultValue;
                else args[i] = p.ParameterType.IsValueType ? Activator.CreateInstance(p.ParameterType) : null;
            }
            var bytes = render.Invoke(null, args) as byte[];
            if (bytes == null || bytes.Length == 0) throw new Exception("Empty image");
            mapWidth = widthAt >= 0 ? (int)args[widthAt] : 0;
            mapHeight = heightAt >= 0 ? (int)args[heightAt] : 0;
            mapMargin = ps.Any(p => p.Name.ToLowerInvariant().Contains("margin")) ? margin : 0;
            mapImage = Convert.ToBase64String(bytes);
        }

        // ---- Carbon AutoWipe calendar ----

        private static string WipeFile()
        {
            return Path.Combine(Environment.CurrentDirectory, "carbon", "modules", "AutoWipe", "config.json");
        }

        private static readonly Regex DateInName = new Regex(@"(\d{4}-\d{2}-\d{2})");
        private static readonly Regex ClockText = new Regex(@"\d{1,2}:\d{2}\s?(am|pm)", RegexOptions.IgnoreCase);

        private JObject ReadWipes(ConsoleSystem.Arg arg)
        {
            string path = WipeFile();
            if (!File.Exists(path)) { arg.ReplyWith("{\"Error\":\"AutoWipe module config not found\"}"); return null; }
            return JObject.Parse(File.ReadAllText(path));
        }

        private void WriteWipes(JObject root)
        {
            File.WriteAllText(WipeFile(), root.ToString(Formatting.Indented));
            ReloadCarbonModule("AutoWipe");
        }

        private static JArray WipeList(JObject root)
        {
            return root["Config"]?["AvailableWipes"] as JArray ?? new JArray();
        }

        [ConsoleCommand("brava.wipes")]
        private void CmdWipes(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            JObject root = ReadWipes(arg);
            if (root == null) return;
            var list = new List<object>();
            int i = 0;
            foreach (JToken w in WipeList(root))
            {
                string cron = (string)w["Cron"] ?? "";
                list.Add(new
                {
                    Index = i++,
                    Name = (string)w["WipeName"] ?? "",
                    Seed = (long?)w["ServerSeed"] ?? 0,
                    Size = (int?)w["MapSize"] ?? 0,
                    Cron = cron,
                    Utc = NextUtc(cron),
                    Full = ((int?)w["Type (0=fullwipe 1=mapwipe)"] ?? 1) == 0
                });
            }
            arg.ReplyWith(JsonConvert.SerializeObject(new
            {
                Enabled = (bool?)root["Enabled"] ?? false,
                Offset = settings.UtcOffsetHours,
                CurrentSeed = World.Seed,
                CurrentSize = World.Size,
                Wipes = list
            }));
        }

        // Unix time of the next run of a "m h dom mon *" cron in UTC, or 0 for other patterns.
        private static long NextUtc(string cron)
        {
            string[] f = cron.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
            int m, h, d, mo;
            if (f.Length != 5 || !int.TryParse(f[0], out m) || !int.TryParse(f[1], out h) || !int.TryParse(f[2], out d) || !int.TryParse(f[3], out mo)) return 0;
            DateTime now = DateTime.UtcNow;
            for (int year = now.Year; year <= now.Year + 1; year++)
            {
                if (d > DateTime.DaysInMonth(year, mo)) continue;
                var t = new DateTime(year, mo, d, h, m, 0, DateTimeKind.Utc);
                if (t > now.AddMinutes(-10)) return new DateTimeOffset(t).ToUnixTimeSeconds();
            }
            return 0;
        }

        [ConsoleCommand("brava.wipes.seed")]
        private void CmdWipeSeed(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            JObject root = ReadWipes(arg);
            if (root == null) return;
            JArray list = WipeList(root);
            int index = arg.GetInt(0, -1);
            long seed;
            if (index < 0 || index >= list.Count) { arg.ReplyWith(T("No wipe with that number.", "No hay un wipe con ese número.")); return; }
            if (!long.TryParse(arg.GetString(1, ""), out seed) || seed < 1 || seed > int.MaxValue) { arg.ReplyWith(T("The seed must be a number between 1 and 2147483647.", "La semilla debe ser un número entre 1 y 2147483647.")); return; }
            list[index]["ServerSeed"] = seed;
            WriteWipes(root);
            arg.ReplyWith(T("Seed saved.", "Semilla guardada."));
        }

        [ConsoleCommand("brava.wipes.time")]
        private void CmdWipeTime(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            DateTime clock;
            if (!DateTime.TryParseExact(arg.GetString(0, "").Trim(), "HH:mm", CultureInfo.InvariantCulture, DateTimeStyles.None, out clock))
            {
                arg.ReplyWith(T("Time must look like 20:00.", "La hora debe verse como 20:00."));
                return;
            }
            JObject root = ReadWipes(arg);
            if (root == null) return;
            int changed = 0, skipped = 0;
            string pretty = clock.ToString("h:mm tt", CultureInfo.InvariantCulture).ToLowerInvariant().Replace(".", "");
            foreach (JToken w in WipeList(root))
            {
                Match date = DateInName.Match((string)w["WipeName"] ?? "");
                DateTime day;
                if (!date.Success || !DateTime.TryParseExact(date.Value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out day)) { skipped++; continue; }
                // Commands run 5 minutes before the wipe; they announce it and restart in 300 seconds.
                DateTime runUtc = day.Date.Add(clock.TimeOfDay).AddMinutes(-5).AddHours(-settings.UtcOffsetHours);
                w["Cron"] = runUtc.Minute + " " + runUtc.Hour + " " + runUtc.Day + " " + runUtc.Month + " *";
                var commands = w["Commands"] as JArray;
                if (commands != null)
                {
                    for (int i = 0; i < commands.Count; i++)
                    {
                        string c = (string)commands[i] ?? "";
                        if (c.StartsWith("say")) commands[i] = ClockText.Replace(c, pretty);
                    }
                }
                changed++;
            }
            WriteWipes(root);
            arg.ReplyWith(T("Wipe time changed on ", "Hora del wipe cambiada en ") + changed + T(" wipes", " wipes") + (skipped > 0 ? T(" (" + skipped + " without a date were left alone)", " (" + skipped + " sin fecha se dejaron igual)") : "") + ".");
        }

        // ---- Kits plugin editor ----

        private static string KitsFile()
        {
            return Path.Combine(Interface.Oxide.DataDirectory, "Kits", "kits_data.json");
        }

        private static readonly string[] Containers = { "MainItems", "WearItems", "BeltItems" };

        [ConsoleCommand("brava.kits")]
        private void CmdKits(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            string path = KitsFile();
            if (!File.Exists(path)) { arg.ReplyWith("{\"Error\":\"Kits data not found\"}"); return; }
            JObject root = JObject.Parse(File.ReadAllText(path));
            var list = new List<object>();
            foreach (JProperty kit in (root["_kits"] as JObject ?? new JObject()).Properties())
            {
                JToken k = kit.Value;
                var items = new List<object>();
                foreach (string c in Containers)
                {
                    foreach (JToken it in k[c] as JArray ?? new JArray())
                    {
                        items.Add(new { Container = c.Replace("Items", "").ToLowerInvariant(), Shortname = (string)it["Shortname"] ?? "", Amount = (int?)it["Amount"] ?? 0 });
                    }
                }
                list.Add(new
                {
                    Name = kit.Name,
                    Description = (string)k["Description"] ?? "",
                    Permission = (string)k["RequiredPermission"] ?? "",
                    Cooldown = (int?)k["Cooldown"] ?? 0,
                    MaxUses = (int?)k["MaximumUses"] ?? 0,
                    Hidden = (bool?)k["IsHidden"] ?? false,
                    Items = items
                });
            }
            arg.ReplyWith(JsonConvert.SerializeObject(list));
        }

        // Kits keeps its data in memory and writes it when it unloads, so edit the file while it is unloaded.
        private string EditKits(Func<JObject, string> change)
        {
            Interface.Oxide.UnloadPlugin("Kits");
            string reply;
            try
            {
                string path = KitsFile();
                JObject root = JObject.Parse(File.ReadAllText(path));
                if (root["_kits"] == null) root["_kits"] = new JObject();
                reply = change(root);
                if (reply == null)
                {
                    File.WriteAllText(path, root.ToString(Formatting.Indented));
                    reply = T("Kits saved.", "Kits guardados.");
                }
            }
            catch (Exception e)
            {
                reply = "Error: " + e.GetBaseException().Message;
            }
            LoadKitsAgain();
            return reply;
        }

        // Unloading puts a plugin on Carbon's ignore list, so do what Carbon's c.load does:
        // ScriptProcessor.ClearIgnore(path) then Prepare(id, path). Oxide loads through its API.
        private void LoadKitsAgain()
        {
            string file = Path.GetFullPath(Path.Combine(Interface.Oxide.PluginDirectory, "Kits.cs"));
            bool requested = false;
            try
            {
                foreach (Assembly asm in AppDomain.CurrentDomain.GetAssemblies())
                {
                    Type community = asm.GetType("Carbon.Community", false);
                    if (community == null) continue;
                    object runtime = community.GetProperty("Runtime", BindingFlags.Public | BindingFlags.Static)?.GetValue(null)
                        ?? community.GetField("Runtime", BindingFlags.Public | BindingFlags.Static)?.GetValue(null);
                    if (runtime == null) break;
                    object processor = runtime.GetType().GetProperty("ScriptProcessor")?.GetValue(runtime)
                        ?? runtime.GetType().GetField("ScriptProcessor")?.GetValue(runtime);
                    if (processor == null) break;
                    MethodInfo clear = processor.GetType().GetMethods().FirstOrDefault(m => m.Name == "ClearIgnore" && m.GetParameters().Length == 1);
                    clear?.Invoke(processor, new object[] { file });
                    MethodInfo prepare = processor.GetType().GetMethods().FirstOrDefault(m => m.Name == "Prepare" && m.GetParameters().Length == 2 && m.GetParameters().All(p => p.ParameterType == typeof(string)));
                    if (prepare != null)
                    {
                        prepare.Invoke(processor, new object[] { "Kits", file });
                        requested = true;
                    }
                    break;
                }
            }
            catch (Exception e)
            {
                PrintWarning("Carbon load of Kits failed: " + e.GetBaseException().Message);
            }
            if (!requested) Interface.Oxide.LoadPlugin("Kits");
            timer.Once(90f, () =>
            {
                if (plugins.Find("Kits") == null) PrintWarning("Kits did not load again. Run: c.load Kits");
            });
        }

        private JObject Kit(JObject root, string name)
        {
            return ((JObject)root["_kits"]).Properties().FirstOrDefault(p => string.Equals(p.Name, name, StringComparison.OrdinalIgnoreCase))?.Value as JObject;
        }

        private static JObject NewItem(string shortname, int amount, int position)
        {
            return new JObject
            {
                ["Shortname"] = shortname, ["DisplayName"] = null, ["Skin"] = 0, ["Amount"] = amount,
                ["Condition"] = 0, ["MaxCondition"] = 0, ["Ammo"] = 0, ["Ammotype"] = null, ["Position"] = position,
                ["Frequency"] = -1, ["BlueprintShortname"] = null, ["Text"] = null, ["Contents"] = null, ["Container"] = null
            };
        }

        [ConsoleCommand("brava.kits.set")]
        private void CmdKitSet(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            string name = arg.GetString(0, "").Trim();
            string field = arg.GetString(1, "").Trim().ToLowerInvariant();
            string value = arg.GetString(2, "");
            arg.ReplyWith(EditKits(root =>
            {
                JObject kit = Kit(root, name);
                if (kit == null) return T("No kit called ", "No hay un kit llamado ") + name;
                int number;
                switch (field)
                {
                    case "description": kit["Description"] = value; break;
                    case "permission": kit["RequiredPermission"] = value.Trim(); break;
                    case "hidden": kit["IsHidden"] = ParseBool(value, false); break;
                    case "cooldown":
                        if (!int.TryParse(value, out number) || number < 0) return T("Cooldown must be seconds, 0 or more.", "La espera debe ser en segundos, 0 o más.");
                        kit["Cooldown"] = number; break;
                    case "uses":
                        if (!int.TryParse(value, out number) || number < 0) return T("Uses must be 0 (unlimited) or more.", "Los usos deben ser 0 (ilimitado) o más.");
                        kit["MaximumUses"] = number; break;
                    default: return "Fields: description, permission, hidden, cooldown, uses";
                }
                return null;
            }));
        }

        [ConsoleCommand("brava.kits.item")]
        private void CmdKitItem(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            string name = arg.GetString(0, "").Trim();
            string where = arg.GetString(1, "main").Trim().ToLowerInvariant();
            string shortname = arg.GetString(2, "").Trim().ToLowerInvariant();
            int amount = arg.GetInt(3, 1);
            string container = where == "wear" ? "WearItems" : where == "belt" ? "BeltItems" : "MainItems";
            int slots = container == "WearItems" ? 8 : container == "BeltItems" ? 6 : 24;
            if (ItemManager.FindItemDefinition(shortname) == null) { arg.ReplyWith(T("Unknown item: ", "Item desconocido: ") + shortname); return; }
            arg.ReplyWith(EditKits(root =>
            {
                JObject kit = Kit(root, name);
                if (kit == null) return T("No kit called ", "No hay un kit llamado ") + name;
                var items = kit[container] as JArray;
                if (items == null) { items = new JArray(); kit[container] = items; }
                JToken existing = items.FirstOrDefault(i => (string)i["Shortname"] == shortname);
                if (amount <= 0)
                {
                    if (existing == null) return T("That item is not in this part of the kit.", "Ese item no está en esa parte del kit.");
                    existing.Remove();
                    return null;
                }
                if (existing != null) { existing["Amount"] = amount; return null; }
                var used = new HashSet<int>(items.Select(i => (int?)i["Position"] ?? -1));
                int pos = Enumerable.Range(0, slots).FirstOrDefault(p => !used.Contains(p));
                if (used.Contains(pos)) return T("That part of the kit is full.", "Esa parte del kit está llena.");
                items.Add(NewItem(shortname, amount, pos));
                return null;
            }));
        }

        [ConsoleCommand("brava.kits.add")]
        private void CmdKitAdd(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            string name = arg.GetString(0, "").Trim().ToLowerInvariant();
            if (!Regex.IsMatch(name, "^[a-z0-9_]{2,24}$")) { arg.ReplyWith(T("Use 2 to 24 letters, numbers or _.", "Usa de 2 a 24 letras, números o _.")); return; }
            arg.ReplyWith(EditKits(root =>
            {
                if (Kit(root, name) != null) return T("That kit already exists.", "Ese kit ya existe.");
                root["_kits"][name] = new JObject
                {
                    ["Name"] = name, ["Description"] = "", ["RequiredPermission"] = "", ["MaximumUses"] = 0, ["RequiredAuth"] = 0,
                    ["Cooldown"] = 3600, ["Cost"] = 0, ["IsHidden"] = true, ["CopyPasteFile"] = "", ["KitImage"] = "",
                    ["MainItems"] = new JArray(), ["WearItems"] = new JArray(), ["BeltItems"] = new JArray()
                };
                return null;
            }));
        }

        [ConsoleCommand("brava.kits.delete")]
        private void CmdKitDelete(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            string name = arg.GetString(0, "").Trim();
            arg.ReplyWith(EditKits(root =>
            {
                JProperty kit = ((JObject)root["_kits"]).Properties().FirstOrDefault(p => string.Equals(p.Name, name, StringComparison.OrdinalIgnoreCase));
                if (kit == null) return T("No kit called ", "No hay un kit llamado ") + name;
                kit.Remove();
                return null;
            }));
        }

        [ConsoleCommand("brava.test")]
        private void CmdTest(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            if (string.IsNullOrWhiteSpace(settings.Topic)) { arg.ReplyWith("Set a topic first."); return; }
            Push(T("Brava test", "Prueba de Brava"), T("Alerts from ", "Las alertas de ") + ConVar.Server.hostname + T(" are working.", " funcionan."));
            arg.ReplyWith("Test sent.");
        }
    }
}
