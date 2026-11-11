using PKHeX.Core;
using System.Text.Json;

EncounterMovesetGenerator.PriorityList =
    [EncounterTypeGroup.Slot, EncounterTypeGroup.Static, EncounterTypeGroup.Egg];

var versions = new Dictionary<string, GameVersion>
{
    ["gold"] = GameVersion.GD,
    ["silver"] = GameVersion.SI,
    ["crystal"] = GameVersion.C,
    ["ruby"] = GameVersion.R,
    ["sapphire"] = GameVersion.S,
    ["emerald"] = GameVersion.E,
    ["firered"] = GameVersion.FR,
    ["leafgreen"] = GameVersion.LG,
    ["diamond"] = GameVersion.D,
    ["pearl"] = GameVersion.P,
    ["platinum"] = GameVersion.Pt,
    ["heartgold"] = GameVersion.HG,
    ["soulsilver"] = GameVersion.SS,
    ["black"] = GameVersion.B,
    ["white"] = GameVersion.W,
    ["black-2"] = GameVersion.B2,
    ["white-2"] = GameVersion.W2,
    ["x"] = GameVersion.X,
    ["y"] = GameVersion.Y,
    ["omega-ruby"] = GameVersion.OR,
    ["alpha-sapphire"] = GameVersion.AS,
    ["sun"] = GameVersion.SN,
    ["moon"] = GameVersion.MN,
    ["ultra-sun"] = GameVersion.US,
    ["ultra-moon"] = GameVersion.UM,
    ["lets-go-pikachu"] = GameVersion.GP,
    ["lets-go-eevee"] = GameVersion.GE,
    ["sword"] = GameVersion.SW,
    ["shield"] = GameVersion.SH,
    ["brilliant-diamond"] = GameVersion.BD,
    ["shining-pearl"] = GameVersion.SP,
    ["legends-arceus"] = GameVersion.PLA,
    ["scarlet"] = GameVersion.SL,
    ["violet"] = GameVersion.VL,
    ["legends-z-a"] = GameVersion.ZA
};

IPersonalTable Table(GameVersion v) => v switch
{
    GameVersion.GD or GameVersion.SI or GameVersion.C => PersonalTable.C,
    GameVersion.R or GameVersion.S => PersonalTable.RS,
    GameVersion.E => PersonalTable.E,
    GameVersion.FR => PersonalTable.FR,
    GameVersion.LG => PersonalTable.LG,
    GameVersion.D or GameVersion.P => PersonalTable.DP,
    GameVersion.Pt => PersonalTable.Pt,
    GameVersion.HG or GameVersion.SS => PersonalTable.HGSS,
    GameVersion.B or GameVersion.W => PersonalTable.BW,
    GameVersion.B2 or GameVersion.W2 => PersonalTable.B2W2,
    GameVersion.X or GameVersion.Y => PersonalTable.XY,
    GameVersion.OR or GameVersion.AS => PersonalTable.AO,
    GameVersion.SN or GameVersion.MN => PersonalTable.SM,
    GameVersion.US or GameVersion.UM => PersonalTable.USUM,
    GameVersion.GP or GameVersion.GE => PersonalTable.GG,
    GameVersion.SW or GameVersion.SH => PersonalTable.SWSH,
    GameVersion.BD or GameVersion.SP => PersonalTable.BDSP,
    GameVersion.PLA => PersonalTable.LA,
    GameVersion.SL or GameVersion.VL => PersonalTable.SV,
    GameVersion.ZA => PersonalTable.ZA,
    _ => throw new ArgumentException()
};

var strings = GameInfo.GetStrings("en");
var data = new List<object>();

foreach (var (key, v) in versions)
{
    var table = Table(v);
    int count = 0;
    for (ushort s = 1; s <= table.MaxSpeciesID; s++)
    {
        var forms = FormConverter.GetFormList(s, strings.Types, strings.forms, v.Context);
        int formCount = Math.Max(1, Math.Max(table[s].FormCount, forms.Length));
        for (byte f = 0; f < formCount; f++)
        {
            if (!table.IsPresentInGame(s, f)) continue;
            var p = EntityBlank.GetBlank(v.Context);
            p.Version = v;
            p.Species = s;
            p.Form = f;
            p.CurrentLevel = 100;

            var encounters = EncounterMovesetGenerator
                .GenerateEncounters(p, ReadOnlyMemory<ushort>.Empty, v)
                .Where(e => e.Shiny == Shiny.Random && e.Version.Contains(v))
                .ToList();
            if (encounters.Count == 0 && f > 0 && FormInfo.IsBattleOnlyForm(s, f, v.Generation))
            {
                p.Form = FormInfo.GetOutOfBattleForm(s, f, v.Generation);
                encounters = EncounterMovesetGenerator
                    .GenerateEncounters(p, ReadOnlyMemory<ushort>.Empty, v)
                    .Where(e => e.Shiny == Shiny.Random && e.Version.Contains(v))
                    .ToList();
            }
            if (encounters.Count == 0) continue;

            var methods = new SortedDictionary<string, SortedSet<string>>();
            void Add(string method, IEncounterable e)
            {
                if (!methods.TryGetValue(method, out var targets))
                    methods[method] = targets = new();
                targets.Add($"{e.Species}:{e.Form}");
            }

            foreach (var e in encounters)
            {
                if (e.IsEgg)
                {
                    Add("Breeding", e);
                    continue;
                }
                var type = e.GetType().Name;
                if (e is EncounterStatic8U)
                {
                    Add("Dynamax Adventures", e);
                    continue;
                }
                if (type.StartsWith("EncounterStatic8N"))
                {
                    Add("Max Raid Battles", e);
                    continue;
                }
                if (type.StartsWith("EncounterTera9"))
                {
                    Add("Tera Raid Battles", e);
                    continue;
                }
                if (e is EncounterGift9a)
                {
                    Add("Fossil revival", e);
                    continue;
                }
                if (e is EncounterSlot9a za)
                {
                    Add(
                        za.Type == SlotType9a.Hyperspace
                            ? "Hyperspace encounters"
                            : "Random encounters",
                        e);
                    continue;
                }
                if (e is EncounterStatic9a)
                {
                    Add("Hyperspace encounters", e);
                    continue;
                }
                if (e is EncounterSlot8a la)
                {
                    Add(la.Type switch
                    {
                        SlotType8a.MassOutbreakRegular => "Mass outbreaks",
                        SlotType8a.MassOutbreakMassive => "Massive mass outbreaks",
                        SlotType8a.Distortion => "Space-time distortions",
                        _ => "Random encounters",
                    }, e);
                    continue;
                }
                if (e is EncounterOutbreak9)
                {
                    Add("Mass outbreaks", e);
                    continue;
                }
                if (e is EncounterFixed9 || e is EncounterStatic9 && e.FixedBall == Ball.None)
                {
                    Add("Fixed symbol encounters", e);
                    continue;
                }
                bool wild = type.StartsWith("EncounterSlot");
                bool gift = !wild && e.FixedBall != Ball.None;
                Add(wild ? "Random encounters" : gift ? "Gift Pokémon" : "Soft resets", e);
                if (!gift && v.Context is not EntityContext.Gen8a and not EntityContext.Gen9a)
                    Add("Runaways", e);
                if (e is EncounterSlot4 dpp && dpp.CanUseRadar
                    || e is EncounterSlot8b bdsp && bdsp.CanUseRadar)
                    Add("Poké Radar", e);
                if (e is EncounterSlot8b ug && ug.IsUnderground) Add("Grand Underground", e);
                if (e is EncounterSlot6XY xy)
                {
                    if (xy.Type == SlotType6.Grass) Add("Poké Radar", e);
                    if (xy.IsFriendSafari) Add("Friend Safari", e);
                    if (xy.Type is SlotType6.Old_Rod or SlotType6.Good_Rod or SlotType6.Super_Rod)
                        Add("Chain fishing", e);
                }
                if (e is EncounterSlot6AO ao)
                {
                    if (ao.CanDexNav) Add("DexNav", e);
                    if (ao.Type is SlotType6.Old_Rod or SlotType6.Good_Rod or SlotType6.Super_Rod)
                        Add("Chain fishing", e);
                }
                if (e is EncounterSlot7 sos && sos.IsSOS) Add("SOS calls", e);
                if (e is EncounterSlot7b) Add("Catch combo", e);
                if (e is EncounterSlot8) Add("Brilliant encounters", e);
            }

            data.Add(new
            {
                game = key,
                species = s,
                form = f,
                formName = f < forms.Length ? forms[f] : "",
                methods,
            });
            count++;
        }
    }
    Console.Error.WriteLine($"{key}: {count}");
}
File.WriteAllText(args[0], JsonSerializer.Serialize(data));
