import argparse
import csv
import json
import re
import subprocess
import tempfile
import unicodedata
import urllib.request
from pathlib import Path

PKHEX_REVISION = "641c26ba100225f3605e5a44b61b3b16e15a3774"
POKEAPI_REVISION = "5ee47a9a9c1229632ec4b473a9c1c53026e005dd"


def normalize(value):
    value = value.replace("♂", "male").replace("♀", "female")
    value = unicodedata.normalize("NFKD", value).lower()
    return re.sub(r"[^a-z0-9]", "", value)


def match_form(form, parent, names, available, species_slug):
    label = form["form_identifier"]
    candidates = {
        normalize(label),
        normalize(names.get(form["id"], "")),
        normalize(form["identifier"].removeprefix(species_slug + "-")),
    }
    candidates |= {
        value.replace("forme", "").replace("form", "").replace("cloak", "").replace("type", "")
        for value in candidates
    }
    candidates |= {
        value.replace("striped", "").replace("plumage", "").replace("breed", "")
        for value in candidates
    }
    candidates |= {
        value.replace("mega", "") + "mega"
        for value in candidates if "mega" in value
    }
    if label in ("alola", "galar", "hisui", "paldea"):
        candidates.add(label)
    if label == "own-tempo":
        candidates.add("dusk")
    if label == "super":
        candidates.add("jumbo")
    if label == "average":
        candidates.add("medium")
    if label == "galar-standard":
        candidates.add("galar")
    if label == "galar-zen":
        return 3
    if int(parent["species_id"]) == 774:
        prefix = "m" if "meteor" in label else "c"
        candidates.add(prefix + normalize(label.replace("-meteor", "")))
    if int(parent["species_id"]) == 869:
        return (int(form["form_order"]) - 1) % 9
    if label == "female" and int(parent["species_id"]) in (668, 592, 593):
        return 0
    if "gmax" in form["identifier"]:
        return 1 if "low-key" in form["identifier"] else 0
    if label == "active" and int(parent["species_id"]) == 716:
        return 0
    for index, name in sorted(available.items()):
        if normalize(name) in candidates:
            return index
    if not label or (form["form_order"] == "1" and parent["is_default"] == "1"):
        return 0
    return None


def build(raw, csv_dir):
    def rows(name):
        with (csv_dir / f"{name}.csv").open(newline="") as file:
            return list(csv.DictReader(file))

    parents = {row["id"]: row for row in rows("pokemon")}
    species = {row["id"]: row for row in rows("pokemon_species")}
    names = {
        row["pokemon_form_id"]: row["form_name"]
        for row in rows("pokemon_form_names")
        if row["local_language_id"] == "9"
    }
    forms = {}
    encounters = {}
    for entry in raw:
        number, form, version = entry["species"], entry["form"], entry["game"]
        forms.setdefault(number, {})[form] = entry["formName"]
        # Gen II's DV formula only permits shiny Unown I and V.
        if version in ("gold", "silver", "crystal") and number == 201 and form not in (8, 21):
            continue
        encounters.setdefault(f"{number}:{form}", {})[version] = entry["methods"]
    pokemon = []
    for row in rows("pokemon_forms"):
        parent = parents[row["pokemon_id"]]
        number = int(parent["species_id"])
        pokemon.append({
            "slug": row["identifier"], "species": species[str(number)]["identifier"],
            "number": number, "sprite": int(row["pokemon_id"]), "form": row["form_identifier"],
            "default": parent["is_default"] == "1" and row["is_default"] == "1",
            "gameFilter": ["sword", "shield"] if "gmax" in row["identifier"] else [],
            "huntForm": match_form(row, parent, names, forms.get(number, {}), species[str(number)]["identifier"]),
        })
    return {
        "sources": {
            "encounters": f"https://github.com/kwsch/PKHeX/tree/{PKHEX_REVISION}/PKHeX.Core/Legality/Encounters",
            "pokemon": f"https://github.com/PokeAPI/pokeapi/tree/{POKEAPI_REVISION}/data/v2/csv",
            "encounterLicense": "GPL-3.0-or-later", "pokemonLicense": "BSD-3-Clause",
            "scope": "Includes breeding with transferred parents, DLC, co-op encounters, and past event unlocks. Excludes Mystery Gift distributions and forced shinies.",
        },
        "pokemon": pokemon,
        "encounters": encounters,
    }


def main():
    parser = argparse.ArgumentParser(description="Build Savepoint's offline hunt catalog from pinned Pokémon encounter data.")
    parser.add_argument("--pkhex", type=Path, required=True, help="PKHeX checkout at the pinned revision")
    parser.add_argument("--dotnet", default="dotnet", help=".NET 10 SDK executable")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "app/src/main/assets/hunt-catalog.json")
    args = parser.parse_args()
    pkhex = args.pkhex.resolve()
    revision = subprocess.check_output(["git", "-C", str(pkhex), "rev-parse", "HEAD"], text=True).strip()
    if revision != PKHEX_REVISION:
        parser.error(f"PKHeX revision must be {PKHEX_REVISION}")
    with tempfile.TemporaryDirectory(prefix="savepoint-hunts-") as directory:
        cache = Path(directory)
        raw_file = cache / "encounters.json"
        subprocess.run([
            args.dotnet, "run", "--project",
            str(Path(__file__).with_name("HuntCatalog.csproj")),
            "-c", "Release", f"-p:PKHeXRoot={pkhex}", "--", str(raw_file),
        ], check=True)
        for name in ("pokemon", "pokemon_species", "pokemon_forms", "pokemon_form_names"):
            url = f"https://raw.githubusercontent.com/PokeAPI/pokeapi/{POKEAPI_REVISION}/data/v2/csv/{name}.csv"
            urllib.request.urlretrieve(url, cache / f"{name}.csv")
        data = build(json.loads(raw_file.read_text()), cache)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
        print(f"Wrote {len(data['pokemon'])} Pokémon / forms and {len(data['encounters'])} hunt targets to {args.output}")


if __name__ == "__main__":
    main()
