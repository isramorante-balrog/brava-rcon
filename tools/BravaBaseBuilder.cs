using System;
using System.Collections.Generic;
using System.Linq;
using Oxide.Core.Plugins;
using UnityEngine;

namespace Oxide.Plugins
{
    // Temporary helper: builds raidable bases high in the sky, saves them with CopyPaste, then removes them.
    // Grid: cell (i,j) centre is (3i, 3j). Level L floor is at y = 3L.
    // Edges: "v,i,j,L" = wall on the west side of cell (i,j) at level L (i == W is the east side),
    //        "h,i,j,L" = wall on the south side of cell (i,j) at level L (j == D is the north side).
    [Info("BravaBaseBuilder", "Brava RCON", "0.3.0")]
    public class BravaBaseBuilder : RustPlugin
    {
        [PluginReference] private Plugin CopyPaste;

        private const string Foundation = "assets/prefabs/building core/foundation/foundation.prefab";
        private const string Wall = "assets/prefabs/building core/wall/wall.prefab";
        private const string Doorway = "assets/prefabs/building core/wall.doorway/wall.doorway.prefab";
        private const string Window = "assets/prefabs/building core/wall.window/wall.window.prefab";
        private const string Frame = "assets/prefabs/building core/wall.frame/wall.frame.prefab";
        private const string Floor = "assets/prefabs/building core/floor/floor.prefab";
        private const string FloorFrame = "assets/prefabs/building core/floor.frame/floor.frame.prefab";
        private const string Hatch = "assets/prefabs/building/floor.ladder.hatch/floor.ladder.hatch.prefab";
        private const string DoorMetal = "assets/prefabs/building/door.hinged/door.hinged.metal.prefab";
        private const string DoorWood = "assets/prefabs/building/door.hinged/door.hinged.wood.prefab";
        private const string DoorDouble = "assets/prefabs/building/door.double.hinged/door.double.hinged.metal.prefab";
        private const string Garage = "assets/prefabs/building/wall.frame.garagedoor/wall.frame.garagedoor.prefab";
        private const string TC = "assets/prefabs/deployable/tool cupboard/cupboard.tool.deployed.prefab";
        private const string BigBox = "assets/prefabs/deployable/large wood storage/box.wooden.large.prefab";
        private const string SmallBox = "assets/prefabs/deployable/woodenbox/woodbox_deployed.prefab";
        private const string Locker = "assets/prefabs/deployable/locker/locker.deployed.prefab";
        private const string Fridge = "assets/prefabs/deployable/fridge/fridge.deployed.prefab";
        private const string Furnace = "assets/prefabs/deployable/furnace/furnace.prefab";
        private const string Bench1 = "assets/prefabs/deployable/tier 1 workbench/workbench1.deployed.prefab";
        private const string Bench2 = "assets/prefabs/deployable/tier 2 workbench/workbench2.deployed.prefab";
        private const string Sleeping = "assets/prefabs/deployable/sleeping bag/sleepingbag_leather_deployed.prefab";
        private const string Turret = "assets/prefabs/npc/autoturret/autoturret_deployed.prefab";
        private const string GunTrap = "assets/prefabs/deployable/single shot trap/guntrap.deployed.prefab";
        private const string Flame = "assets/prefabs/npc/flame turret/flameturret.deployed.prefab";
        private const string Mine = "assets/prefabs/deployable/landmine/landmine.prefab";

        private class Item
        {
            public string Prefab;
            public float X, Z, Rot;
            public int Level;
        }

        private class Layout
        {
            public int W, D, Levels = 1;
            public BuildingGrade.Enum Grade;
            public List<string> Inner = new List<string>();
            // Edge -> "doorway:<door>", "window", "frame:<door>", "none"
            public Dictionary<string, string> Special = new Dictionary<string, string>();
            public HashSet<string> Hatches = new HashSet<string>(); // "i,j,L": ceiling of level L at cell (i,j)
            public List<Item> Items = new List<Item>();

            public Layout Walls(params string[] edges) { Inner.AddRange(edges); return this; }
            public Layout Door(string edge, string door = DoorMetal) { Special[edge] = "doorway:" + door; return this; }
            public Layout Win(params string[] edges) { foreach (var e in edges) Special[e] = "window"; return this; }
            public Layout Wide(string edge, string door) { Special[edge] = "frame:" + door; return this; }
            public Layout Up(int i, int j, int level) { Hatches.Add(i + "," + j + "," + level); return this; }
            public Layout Put(string prefab, float x, float z, int level = 0, float rot = 0f)
            {
                Items.Add(new Item { Prefab = prefab, X = x, Z = z, Level = level, Rot = rot });
                return this;
            }
        }

        private readonly List<BaseEntity> spawned = new List<BaseEntity>();

        private Layout Make(string name)
        {
            var l = new Layout();
            switch (name)
            {
                // ---------- first set (one floor) ----------
                case "choza":
                    l.W = 2; l.D = 2; l.Grade = BuildingGrade.Enum.Stone;
                    l.Door("h,0,0,0")
                     .Put(TC, 3f, 3.6f, 0, 180f).Put(BigBox, 0f, 3.8f).Put(BigBox, 3.8f, 0.3f, 0, 90f).Put(Sleeping, -0.8f, 2.5f);
                    break;
                case "bunker":
                    l.W = 2; l.D = 3; l.Grade = BuildingGrade.Enum.Stone;
                    l.Walls("h,0,1,0", "h,1,1,0", "v,1,0,0").Door("h,0,0,0").Door("h,0,1,0")
                     .Put(TC, 3f, 6.9f, 0, 180f).Put(BigBox, 0f, 6.8f).Put(BigBox, 0f, 4.1f).Put(Furnace, 3.6f, 3.6f).Put(SmallBox, 3f, 0f);
                    break;
                case "fortin":
                    l.W = 3; l.D = 3; l.Grade = BuildingGrade.Enum.Stone;
                    l.Walls("v,1,1,0", "v,2,1,0", "h,1,1,0", "h,1,2,0").Door("h,1,0,0").Door("h,1,1,0")
                     .Put(TC, 3f, 3.9f, 0, 180f).Put(SmallBox, 2.1f, 3.9f, 0, 90f).Put(BigBox, 0f, 6.8f).Put(BigBox, 6f, 6.8f)
                     .Put(Furnace, 6.3f, 0.3f).Put(Bench1, 0f, 0.3f).Put(SmallBox, 0f, 3f, 0, 90f).Put(SmallBox, 6f, 3f, 0, 90f);
                    break;
                case "metalica":
                    l.W = 3; l.D = 2; l.Grade = BuildingGrade.Enum.Metal;
                    l.Walls("v,1,0,0", "v,1,1,0", "v,2,0,0", "v,2,1,0").Door("v,0,0,0").Door("v,1,0,0").Door("v,2,1,0")
                     .Put(TC, 6.3f, 3.9f, 0, 180f).Put(BigBox, 6.3f, 0f, 0, 90f).Put(BigBox, 3f, 3.8f).Put(SmallBox, 3.6f, -0.9f)
                     .Put(Furnace, 0f, 3.6f).Put(SmallBox, 0.6f, -0.9f);
                    break;
                case "torre":
                    l.W = 2; l.D = 2; l.Grade = BuildingGrade.Enum.Metal;
                    l.Walls("v,1,0,0", "v,1,1,0", "h,0,1,0", "h,1,1,0").Door("h,0,0,0").Door("v,1,0,0").Door("h,1,1,0")
                     .Put(TC, 3f, 3.6f, 0, 180f).Put(BigBox, 3.8f, 0f, 0, 90f).Put(SmallBox, 0f, 3.6f);
                    break;

                // ---------- second set ----------
                case "nido":
                    // 2x2 metal, two floors. Ground: airlock with a shotgun trap, ladder room, sealed loot room.
                    // Upstairs: TC and loot behind barred-free windows, turret on the roof.
                    l.W = 2; l.D = 2; l.Levels = 2; l.Grade = BuildingGrade.Enum.Metal;
                    l.Walls("v,1,0,0", "h,0,1,0", "h,1,1,0", "v,1,1,0")
                     .Door("h,0,0,0").Door("v,1,0,0").Door("h,1,1,0")
                     .Up(1, 0, 0)
                     .Win("h,0,0,1", "h,1,2,1", "v,0,1,1", "v,2,0,1")
                     .Put(GunTrap, 0f, 0.9f, 0, 180f)
                     .Put(BigBox, 0f, 3.8f).Put(BigBox, 3.8f, 3f, 0, 90f)
                     .Put(TC, 0f, 3.6f, 1, 180f).Put(BigBox, 3.8f, 3f, 1, 90f).Put(Locker, 0f, -0.9f, 1)
                     .Put(Turret, 1.5f, 1.5f, 2, 180f);
                    break;
                case "panal":
                    // 4x4 stone honeycomb: 2x2 core with the TC, sealed ring rooms in the corners hold the loot.
                    l.W = 4; l.D = 4; l.Grade = BuildingGrade.Enum.Stone;
                    l.Walls("v,1,1,0", "v,1,2,0", "v,3,1,0", "v,3,2,0", "h,1,1,0", "h,2,1,0", "h,1,3,0", "h,2,3,0",
                            "v,1,0,0", "v,3,0,0", "v,1,3,0", "v,3,3,0", "h,0,1,0", "h,0,3,0", "h,3,1,0", "h,3,3,0")
                     .Door("h,2,0,0").Door("h,2,1,0")
                     .Put(Flame, 3.2f, 0.3f, 0, 90f)
                     .Put(TC, 4.5f, 6.9f, 0, 180f).Put(BigBox, 3f, 6.85f).Put(Bench2, 3f, 3.7f).Put(Furnace, 6.4f, 4f)
                     .Put(BigBox, 0f, 0f, 0, 90f).Put(BigBox, 9f, 0f, 0, 90f).Put(BigBox, 0f, 9f, 0, 90f).Put(BigBox, 9f, 9f, 0, 90f)
                     .Put(Locker, 0f, 4.5f, 0, 90f).Put(Locker, 9f, 4.5f, 0, 270f).Put(SmallBox, 4.5f, 9f);
                    break;
                case "garaje":
                    // 3x2 metal, garage door into a hall, loot rooms on both sides behind doors, turret on the roof.
                    l.W = 3; l.D = 2; l.Grade = BuildingGrade.Enum.Metal;
                    l.Walls("v,1,0,0", "v,1,1,0", "v,2,0,0", "v,2,1,0").Wide("h,1,0,0", Garage)
                     .Door("v,1,1,0").Door("v,2,1,0")
                     .Put(GunTrap, 3f, 3.9f, 0, 180f)
                     .Put(TC, 6.3f, 3.9f, 0, 180f).Put(BigBox, 6.3f, 0f, 0, 90f).Put(Locker, 6.8f, 2.0f, 0, 270f)
                     .Put(BigBox, -0.3f, 0f, 0, 90f).Put(Fridge, 0f, 3.8f, 0, 180f).Put(Furnace, -0.3f, 2.4f)
                     .Put(Turret, 3f, 1.5f, 1, 180f);
                    break;
                case "atalaya":
                    // 2x2 metal watchtower, two floors with peek windows and two roof turrets.
                    l.W = 2; l.D = 2; l.Levels = 2; l.Grade = BuildingGrade.Enum.Metal;
                    l.Walls("v,1,0,0", "v,1,1,0").Door("h,0,0,0").Door("v,1,1,0")
                     .Up(0, 1, 0)
                     .Win("h,0,0,1", "h,1,0,1", "h,0,2,1", "h,1,2,1", "v,0,0,1", "v,2,1,1")
                     .Put(BigBox, 3.8f, 0f, 0, 90f).Put(BigBox, 3.8f, 3.6f, 0, 90f).Put(Furnace, -0.9f, 0.9f)
                     .Put(TC, 3f, 3.6f, 1, 180f).Put(BigBox, 0f, -0.8f, 1).Put(Locker, 3.8f, 0f, 1, 270f)
                     .Put(Turret, 0f, 0f, 2, 180f).Put(Turret, 3f, 3f, 2, 0f);
                    break;
                case "cuartel":
                    // 4x3 stone, two floors: airlock, ground loot rooms, ladder to a honeycombed TC core upstairs.
                    l.W = 4; l.D = 3; l.Levels = 2; l.Grade = BuildingGrade.Enum.Stone;
                    l.Walls("h,0,1,0", "h,1,1,0", "h,2,1,0", "h,3,1,0", "v,1,0,0", "v,2,0,0", "v,3,0,0",
                            "v,2,1,0", "v,2,2,0",
                            "v,1,0,1", "v,1,1,1", "v,1,2,1", "v,3,0,1", "v,3,1,1", "v,3,2,1", "h,1,1,1", "h,2,1,1", "h,1,2,1", "h,2,2,1")
                     .Door("h,1,0,0").Door("h,1,1,0").Door("v,2,1,0").Door("h,3,1,0")
                     .Up(1, 2, 0)
                     .Door("h,1,2,1").Door("v,1,2,1")
                     .Win("h,0,0,1", "h,3,0,1", "h,0,3,1", "h,3,3,1")
                     .Put(GunTrap, 3f, 0.9f, 0, 180f).Put(SmallBox, 0f, 0f).Put(SmallBox, 6f, 0f).Put(SmallBox, 9f, 0f)
                     .Put(BigBox, 0f, 6.8f).Put(Furnace, 0f, 3.6f).Put(Bench2, 6f, 6.8f, 0, 0f).Put(BigBox, 9f, 6.8f).Put(Locker, 9.8f, 3f, 0, 270f)
                     .Put(TC, 4.5f, 3.6f, 1, 180f).Put(BigBox, 4.5f, 2.4f, 1)
                     .Put(BigBox, 0f, 6.8f, 1).Put(BigBox, 9f, 6.8f, 1).Put(Locker, -0.8f, 0f, 1, 90f).Put(Fridge, 9.8f, 0f, 1, 270f)
                     .Put(Turret, 1.5f, 3f, 2, 180f).Put(Turret, 7.5f, 3f, 2, 0f);
                    break;
                case "trampa":
                    // 3x3 metal that looks easy: two airlocks with traps and mines before a small loot core.
                    l.W = 3; l.D = 3; l.Grade = BuildingGrade.Enum.Metal;
                    l.Walls("h,0,1,0", "h,2,1,0", "v,1,1,0", "v,2,1,0", "h,1,2,0", "v,1,2,0", "v,2,2,0")
                     .Door("h,1,0,0").Door("h,1,1,0").Door("h,1,2,0")
                     .Put(Mine, 3f, 0.2f).Put(GunTrap, 2.0f, 3.0f, 0, 90f)
                     .Put(Flame, 3.8f, 6.9f, 0, 180f)
                     .Put(TC, 2.2f, 6.9f, 0, 180f).Put(BigBox, 0f, 6.8f).Put(BigBox, 6f, 6.8f)
                     .Put(SmallBox, 0f, 0f).Put(SmallBox, 6f, 0f).Put(Locker, -0.8f, 3f, 0, 90f).Put(Locker, 6.8f, 3f, 0, 270f);
                    break;
                default:
                    return null;
            }
            return l;
        }

        private T Spawn<T>(string prefab, Vector3 pos, Quaternion rot) where T : BaseEntity
        {
            var e = GameManager.server.CreateEntity(prefab, pos, rot) as T;
            if (e == null) return null;
            e.Spawn();
            spawned.Add(e);
            return e;
        }

        private void Block(string prefab, Vector3 pos, Quaternion rot, BuildingGrade.Enum grade, uint buildingId)
        {
            var b = GameManager.server.CreateEntity(prefab, pos, rot) as BuildingBlock;
            if (b == null) return;
            b.buildingID = buildingId;
            b.grade = grade;
            b.Spawn();
            b.SetHealthToMax();
            b.SendNetworkUpdate();
            spawned.Add(b);
        }

        private static void Edge(string edge, out Vector3 pos, out Quaternion rot, out int level)
        {
            string[] p = edge.Split(',');
            int i = int.Parse(p[1]), j = int.Parse(p[2]);
            level = p.Length > 3 ? int.Parse(p[3]) : 0;
            float y = level * 3f;
            if (p[0] == "v")
            {
                pos = new Vector3(i * 3f - 1.5f, y, j * 3f);
                rot = Quaternion.identity;
            }
            else
            {
                pos = new Vector3(i * 3f, y, j * 3f - 1.5f);
                rot = Quaternion.Euler(0f, 90f, 0f);
            }
        }

        [ConsoleCommand("bravabuild.make")]
        private void CmdMake(ConsoleSystem.Arg arg)
        {
            if (arg.Connection != null) return;
            string layoutName = arg.GetString(0, "");
            string file = arg.GetString(1, "brava_" + layoutName);
            Layout l = Make(layoutName);
            if (l == null) { arg.ReplyWith("Unknown layout"); return; }
            if (CopyPaste == null) { arg.ReplyWith("CopyPaste not loaded"); return; }
            BasePlayer admin = BasePlayer.activePlayerList.FirstOrDefault(p => p.IsAdmin);
            if (admin == null) { arg.ReplyWith("An admin must be online"); return; }
            Clear();

            Vector3 origin = new Vector3(arg.GetFloat(2, 1500f), 700f, arg.GetFloat(3, 1500f));
            uint id = (uint)BuildingManager.server.NewBuildingID();

            for (int i = 0; i < l.W; i++)
            for (int j = 0; j < l.D; j++)
            {
                Block(Foundation, origin + new Vector3(i * 3f, 0f, j * 3f), Quaternion.identity, l.Grade, id);
                for (int level = 0; level < l.Levels; level++)
                {
                    Vector3 ceiling = origin + new Vector3(i * 3f, (level + 1) * 3f, j * 3f);
                    if (l.Hatches.Contains(i + "," + j + "," + level))
                    {
                        Block(FloorFrame, ceiling, Quaternion.identity, l.Grade, id);
                        Spawn<BaseEntity>(Hatch, ceiling, Quaternion.identity);
                    }
                    else
                    {
                        Block(Floor, ceiling, Quaternion.identity, l.Grade, id);
                    }
                }
            }

            var edges = new List<string>();
            for (int level = 0; level < l.Levels; level++)
            {
                for (int i = 0; i < l.W; i++)
                {
                    edges.Add("h," + i + ",0," + level);
                    edges.Add("h," + i + "," + l.D + "," + level);
                }
                for (int j = 0; j < l.D; j++)
                {
                    edges.Add("v,0," + j + "," + level);
                    edges.Add("v," + l.W + "," + j + "," + level);
                }
            }
            edges.AddRange(l.Inner);

            foreach (string edge in edges.Distinct())
            {
                Vector3 pos; Quaternion rot; int level;
                Edge(edge, out pos, out rot, out level);
                string kind;
                if (!l.Special.TryGetValue(edge, out kind)) kind = "wall";
                if (kind == "none") continue;
                if (kind == "window")
                {
                    Block(Window, origin + pos, rot, l.Grade, id);
                }
                else if (kind.StartsWith("doorway:"))
                {
                    Block(Doorway, origin + pos, rot, l.Grade, id);
                    Spawn<BaseEntity>(kind.Substring(8), origin + pos, rot);
                }
                else if (kind.StartsWith("frame:"))
                {
                    Block(Frame, origin + pos, rot, l.Grade, id);
                    Spawn<BaseEntity>(kind.Substring(6), origin + pos, rot);
                }
                else
                {
                    Block(Wall, origin + pos, rot, l.Grade, id);
                }
            }

            foreach (Item item in l.Items)
            {
                // Upper floors and the roof are floor tiles whose top is 0.1 above their centre.
                float y = item.Level * 3f + (item.Level > 0 ? 0.1f : 0f);
                Spawn<BaseEntity>(item.Prefab, origin + new Vector3(item.X, y, item.Z), Quaternion.Euler(0f, item.Rot, 0f));
            }

            Vector3 source = origin + new Vector3((l.W - 1) * 1.5f, 0f, (l.D - 1) * 1.5f);
            int count = spawned.Count;
            object result = CopyPaste.Call("TryCopy", source, Vector3.zero, file, 0f,
                new[] { "method", "proximity", "radius", "3" }, admin.IPlayer, (Action)(() =>
                {
                    Puts("Copied " + file + " (" + count + " entities)");
                    Clear();
                }));
            arg.ReplyWith("Built " + layoutName + " with " + count + " entities; copy result: " + (result ?? "null"));
        }

        [ConsoleCommand("bravabuild.clear")]
        private void CmdClear(ConsoleSystem.Arg arg)
        {
            if (arg.Connection != null) return;
            int n = spawned.Count;
            Clear();
            arg.ReplyWith("Removed " + n);
        }

        private void Clear()
        {
            foreach (var e in spawned.ToList())
            {
                if (e != null && !e.IsDestroyed) e.Kill();
            }
            spawned.Clear();
        }

        private void Unload()
        {
            Clear();
        }
    }
}
