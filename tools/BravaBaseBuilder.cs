using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Oxide.Core.Plugins;
using UnityEngine;

namespace Oxide.Plugins
{
    // Temporary helper: builds simple raidable bases high in the sky, saves them with CopyPaste, then removes them.
    [Info("BravaBaseBuilder", "Brava RCON", "0.2.0")]
    public class BravaBaseBuilder : RustPlugin
    {
        [PluginReference] private Plugin CopyPaste;

        private const string Foundation = "assets/prefabs/building core/foundation/foundation.prefab";
        private const string Wall = "assets/prefabs/building core/wall/wall.prefab";
        private const string Doorway = "assets/prefabs/building core/wall.doorway/wall.doorway.prefab";
        private const string Floor = "assets/prefabs/building core/floor/floor.prefab";
        private const string DoorMetal = "assets/prefabs/building/door.hinged/door.hinged.metal.prefab";
        private const string DoorWood = "assets/prefabs/building/door.hinged/door.hinged.wood.prefab";
        private const string TC = "assets/prefabs/deployable/tool cupboard/cupboard.tool.deployed.prefab";
        private const string BigBox = "assets/prefabs/deployable/large wood storage/box.wooden.large.prefab";
        private const string SmallBox = "assets/prefabs/deployable/woodenbox/woodbox_deployed.prefab";
        private const string Furnace = "assets/prefabs/deployable/furnace/furnace.prefab";
        private const string Bench = "assets/prefabs/deployable/tier 1 workbench/workbench1.deployed.prefab";
        private const string Sleeping = "assets/prefabs/deployable/sleeping bag/sleepingbag_leather_deployed.prefab";

        private class Layout
        {
            public int W, D;
            public BuildingGrade.Enum Grade;
            // Edges as "v,i,j" (wall on the west side of cell i,j; i == W is the east side)
            // or "h,i,j" (wall on the south side of cell i,j; j == D is the north side).
            public List<string> InnerWalls = new List<string>();
            public Dictionary<string, string> Doors = new Dictionary<string, string>(); // edge -> door prefab
            public List<KeyValuePair<string, Vector3>> Items = new List<KeyValuePair<string, Vector3>>(); // prefab -> local pos (y rot in z of key? no)
            public List<float> ItemRot = new List<float>();
        }

        private readonly List<BaseEntity> spawned = new List<BaseEntity>();

        private void AddItem(Layout l, string prefab, float x, float z, float yRot)
        {
            l.Items.Add(new KeyValuePair<string, Vector3>(prefab, new Vector3(x, 0f, z)));
            l.ItemRot.Add(yRot);
        }

        private Layout Make(string name)
        {
            var l = new Layout();
            switch (name)
            {
                case "choza":
                    // 2x2 stone hut, one metal door, TC and two boxes.
                    l.W = 2; l.D = 2; l.Grade = BuildingGrade.Enum.Stone;
                    l.Doors["h,0,0"] = DoorMetal;
                    AddItem(l, TC, 3f, 3.6f, 180f);
                    AddItem(l, BigBox, 0f, 3.8f, 0f);
                    AddItem(l, BigBox, 3.8f, 0.3f, 90f);
                    AddItem(l, Sleeping, -0.8f, 2.5f, 0f);
                    break;
                case "bunker":
                    // 2x3 stone, airlock at the front then the main room.
                    l.W = 2; l.D = 3; l.Grade = BuildingGrade.Enum.Stone;
                    l.InnerWalls.Add("h,0,1"); l.InnerWalls.Add("h,1,1"); l.InnerWalls.Add("v,1,0");
                    l.Doors["h,0,0"] = DoorMetal;
                    l.Doors["h,0,1"] = DoorMetal;
                    AddItem(l, TC, 3f, 6.9f, 180f);
                    AddItem(l, BigBox, 0f, 6.8f, 0f);
                    AddItem(l, BigBox, 0f, 4.1f, 0f);
                    AddItem(l, Furnace, 3.6f, 3.6f, 0f);
                    AddItem(l, SmallBox, 3f, 0f, 0f);
                    break;
                case "fortin":
                    // 3x3 stone, outer ring around a core room with the TC.
                    l.W = 3; l.D = 3; l.Grade = BuildingGrade.Enum.Stone;
                    l.InnerWalls.AddRange(new[] { "v,1,1", "v,2,1", "h,1,1", "h,1,2" });
                    l.Doors["h,1,0"] = DoorMetal;
                    l.Doors["h,1,1"] = DoorMetal;
                    AddItem(l, TC, 3f, 3.9f, 180f);
                    AddItem(l, SmallBox, 2.1f, 3.9f, 90f);
                    AddItem(l, BigBox, 0f, 6.8f, 0f);
                    AddItem(l, BigBox, 6f, 6.8f, 0f);
                    AddItem(l, Furnace, 6.3f, 0.3f, 0f);
                    AddItem(l, Bench, 0f, 0.3f, 0f);
                    AddItem(l, SmallBox, 0f, 3f, 90f);
                    AddItem(l, SmallBox, 6f, 3f, 90f);
                    break;
                case "metalica":
                    // 3x2 metal, two rooms in a row, three doors.
                    l.W = 3; l.D = 2; l.Grade = BuildingGrade.Enum.Metal;
                    l.InnerWalls.AddRange(new[] { "v,1,0", "v,1,1", "v,2,0", "v,2,1" });
                    l.Doors["v,0,0"] = DoorMetal;
                    l.Doors["v,1,0"] = DoorMetal;
                    l.Doors["v,2,1"] = DoorMetal;
                    AddItem(l, TC, 6.3f, 3.9f, 180f);
                    AddItem(l, BigBox, 6.3f, 0f, 90f);
                    AddItem(l, BigBox, 3f, 3.8f, 0f);
                    AddItem(l, SmallBox, 3.6f, -0.9f, 0f);
                    AddItem(l, Furnace, 0f, 3.6f, 0f);
                    AddItem(l, SmallBox, 0.6f, -0.9f, 0f);
                    break;
                case "torre":
                    // 2x2 metal with walls on every side and doors through two inner walls.
                    l.W = 2; l.D = 2; l.Grade = BuildingGrade.Enum.Metal;
                    l.InnerWalls.AddRange(new[] { "v,1,0", "v,1,1", "h,0,1", "h,1,1" });
                    l.Doors["h,0,0"] = DoorMetal;
                    l.Doors["v,1,0"] = DoorMetal;
                    l.Doors["h,1,1"] = DoorMetal;
                    AddItem(l, TC, 3f, 3.6f, 180f);
                    AddItem(l, BigBox, 3.8f, 0f, 90f);
                    AddItem(l, SmallBox, 0f, 3.6f, 0f);
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

        private BuildingBlock Block(string prefab, Vector3 pos, Quaternion rot, BuildingGrade.Enum grade, uint buildingId)
        {
            var b = GameManager.server.CreateEntity(prefab, pos, rot) as BuildingBlock;
            if (b == null) return null;
            b.buildingID = buildingId;
            b.grade = grade;
            b.Spawn();
            b.SetHealthToMax();
            b.SendNetworkUpdate();
            spawned.Add(b);
            return b;
        }

        // Centre and rotation of an edge in local coordinates (cell i,j centre = (3i, 3j)).
        private static void Edge(string edge, out Vector3 pos, out Quaternion rot)
        {
            string[] p = edge.Split(',');
            int i = int.Parse(p[1]), j = int.Parse(p[2]);
            if (p[0] == "v")
            {
                pos = new Vector3(i * 3f - 1.5f, 0f, j * 3f);
                rot = Quaternion.identity;
            }
            else
            {
                pos = new Vector3(i * 3f, 0f, j * 3f - 1.5f);
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
            Clear();

            Vector3 origin = new Vector3(arg.GetFloat(2, 1500f), 700f, arg.GetFloat(3, 1500f));
            uint id = (uint)BuildingManager.server.NewBuildingID();
            var outer = new HashSet<string>();
            for (int i = 0; i < l.W; i++)
            {
                outer.Add("h," + i + ",0");
                outer.Add("h," + i + "," + l.D);
            }
            for (int j = 0; j < l.D; j++)
            {
                outer.Add("v,0," + j);
                outer.Add("v," + l.W + "," + j);
            }

            for (int i = 0; i < l.W; i++)
            for (int j = 0; j < l.D; j++)
            {
                Block(Foundation, origin + new Vector3(i * 3f, 0f, j * 3f), Quaternion.identity, l.Grade, id);
                Block(Floor, origin + new Vector3(i * 3f, 3f, j * 3f), Quaternion.identity, l.Grade, id);
            }

            foreach (string edge in outer.Concat(l.InnerWalls).Distinct())
            {
                Vector3 pos; Quaternion rot;
                Edge(edge, out pos, out rot);
                string door;
                bool isDoor = l.Doors.TryGetValue(edge, out door);
                Block(isDoor ? Doorway : Wall, origin + pos, rot, l.Grade, id);
                if (isDoor)
                {
                    var d = Spawn<Door>(door, origin + pos, rot);
                }
            }

            for (int k = 0; k < l.Items.Count; k++)
            {
                Vector3 local = l.Items[k].Value;
                var e = Spawn<BaseEntity>(l.Items[k].Key, origin + local, Quaternion.Euler(0f, l.ItemRot[k], 0f));
            }

            // Copy from the middle of the base, standing on the foundations.
            Vector3 source = origin + new Vector3((l.W - 1) * 1.5f, 0f, (l.D - 1) * 1.5f);
            int count = spawned.Count;
            // CopyPaste needs a connected player to own the copy; use an online admin.
            BasePlayer admin = BasePlayer.activePlayerList.FirstOrDefault(p => p.IsAdmin);
            if (admin == null) { Clear(); arg.ReplyWith("An admin must be online"); return; }
            object result = CopyPaste.Call("TryCopy", source, Vector3.zero, file, 0f, new[] { "method", "proximity", "radius", "3" }, admin.IPlayer, (Action)(() =>
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
