// BravaCompanion - server side of the Brava RCON Android app.
// Sends phone alerts through ntfy and runs scheduled commands.
// Install: copy this file into carbon/plugins or oxide/plugins.
// License: GPL-3.0

using System;
using System.Collections.Generic;
using Newtonsoft.Json;
using Oxide.Core.Libraries;

namespace Oxide.Plugins
{
    [Info("BravaCompanion", "Brava RCON", "1.0.1")]
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
        }

        private Settings settings = new Settings();
        private readonly Dictionary<int, string> lastDailyRun = new Dictionary<int, string>();
        private readonly Dictionary<int, DateTime> nextRepeat = new Dictionary<int, DateTime>();
        private bool wiped;

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
            timer.Every(20f, Tick);
            if (wiped && settings.NotifyWipe) Push("Wipe finished", ConVar.Server.hostname + " is up with a new map.");
            else if (initial && settings.NotifyStart) Push("Server started", ConVar.Server.hostname + " is online.");
            wiped = false;
        }

        private void OnPlayerConnected(BasePlayer player)
        {
            if (player == null || !settings.NotifyJoins) return;
            Push("Player joined", player.displayName + " (" + BasePlayer.activePlayerList.Count + " online)");
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
                    Push("Chat alert", player.displayName + ": " + message);
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
                        Server.Command(item.Command);
                    }
                }
                else if (item.Time == hhmm)
                {
                    string last;
                    if (lastDailyRun.TryGetValue(i, out last) && last == today) continue;
                    lastDailyRun[i] = today;
                    Server.Command(item.Command);
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

        [ConsoleCommand("brava.test")]
        private void CmdTest(ConsoleSystem.Arg arg)
        {
            if (!Allowed(arg)) return;
            if (string.IsNullOrWhiteSpace(settings.Topic)) { arg.ReplyWith("Set a topic first."); return; }
            Push("Brava test", "Alerts from " + ConVar.Server.hostname + " are working.");
            arg.ReplyWith("Test sent.");
        }
    }
}
