package com.cmyksoda.savepoint

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PokemonChoice(
    val slug: String,
    val species: String,
    val number: Int,
    val form: String,
    val default: Boolean = form.isBlank(),
) {
    val label: String
        get() = displayPokemonName(slug)
}

fun parsePokemonChoices(
    pokemonCsv: String,
    speciesCsv: String,
    formCsv: String,
): List<PokemonChoice> {
    fun rows(csv: String) =
        csv.lineSequence().drop(1).filter { it.isNotBlank() }.map { it.split(',') }
    val species = rows(speciesCsv).associate { it[0].toInt() to it[1] }
    val pokemon = rows(pokemonCsv).associate { it[0].toInt() to (it[2].toInt() to (it[7] == "1")) }
    return rows(formCsv)
        .mapNotNull { row ->
            val parent = pokemon[row[3].toInt()] ?: return@mapNotNull null
            val number = parent.first
            val name = species[number] ?: return@mapNotNull null
            PokemonChoice(row[1], name, number, row[2], parent.second && row[5] == "1")
        }
        .distinctBy { it.slug }
        .toList()
}

fun searchPokemon(choices: List<PokemonChoice>, query: String): List<PokemonChoice> =
    choices
        .mapNotNull { choice ->
            searchMatchScore(
                    "${choice.label} ${choice.number} ${when (choice.form) { "alola" -> "Alolan"
 "galar" -> "Galarian"
 "hisui" -> "Hisuian"
 "paldea" -> "Paldean"
 else -> "" }}",
                    query,
                )
                ?.let { it to choice }
        }
        .sortedWith(
            compareBy<Pair<Int, PokemonChoice>> { it.first }
                .thenBy { it.second.number }
                .thenBy { it.second.slug }
        )
        .map { it.second }

class PokemonCatalog(private val context: Context) {
    private var memory: List<PokemonChoice>? = null

    suspend fun all(): List<PokemonChoice> =
        withContext(Dispatchers.IO) {
            memory?.let {
                return@withContext it
            }
            val file = File(context.cacheDir, "pokemon-form-index.json")
            fun read(): List<PokemonChoice> {
                val items = JSONArray(file.readText())
                return (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    PokemonChoice(
                        item.getString("slug"),
                        item.getString("species"),
                        item.getInt("number"),
                        item.getString("form"),
                        item.optBoolean("default", false),
                    )
                }
            }
            if (file.isFile) {
                val saved = runCatching { read() }.getOrNull()
                if (!saved.isNullOrEmpty()) {
                    memory = saved
                    return@withContext saved
                }
            }
            fun csv(name: String) =
                String(
                    fetchBytes(
                        "https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv/$name.csv"
                    )
                )
            val choices =
                parsePokemonChoices(csv("pokemon"), csv("pokemon_species"), csv("pokemon_forms"))
            require(choices.isNotEmpty()) { "The Pokémon list is unavailable" }
            val data = JSONArray()
            choices.forEach { choice ->
                data.put(
                    JSONObject()
                        .put("slug", choice.slug)
                        .put("species", choice.species)
                        .put("number", choice.number)
                        .put("form", choice.form)
                        .put("default", choice.default)
                )
            }
            val temporary = File.createTempFile("pokemon-index-", ".tmp", context.cacheDir)
            try {
                temporary.writeText(data.toString())
                require(temporary.renameTo(file))
            } finally {
                temporary.delete()
            }
            memory = choices
            choices
        }

    suspend fun find(slug: String): PokemonChoice {
        val choices = all()
        return choices.firstOrNull { it.slug == slug }
            ?: choices.firstOrNull { it.number == slug.toIntOrNull() && it.default }
            ?: choices.firstOrNull { it.species == slug && it.form in setOf("", "normal", "land") }
            ?: throw IllegalArgumentException("Select a Pokémon or form from search")
    }
}

fun serebiiShinyCandidates(pokemon: PokemonChoice, version: String): List<String> {
    val directories =
        when (version) {
            "gold" -> listOf("Gold")
            "silver" -> listOf("Silver")
            "crystal" -> listOf("Crystal")
            "ruby-sapphire" -> listOf("RS", "RuSa")
            "emerald" -> listOf("Em", "E")
            "firered-leafgreen" -> listOf("FRLG")
            "diamond-pearl" -> listOf("DP")
            "platinum" -> listOf("Pl")
            "heartgold-soulsilver" -> listOf("HGSS")
            "black-white",
            "black-2-white-2" -> listOf("BW")
            "x-y",
            "omegaruby-alphasapphire" -> listOf("XY")
            "sun-moon",
            "ultra-sun-ultra-moon" -> listOf("SM")
            "lets-go" -> listOf("LGPE")
            "sword-shield" -> listOf("SWSH")
            "brilliant-diamond-shining-pearl" -> listOf("BDSP")
            "legends-arceus" -> listOf("LA")
            "scarlet-violet" -> listOf("SV/new", "SV")
            "legends-z-a" -> listOf("ZA")
            else -> emptyList()
        }
    val suffixes =
        when {
            pokemon.species == "shaymin" && version == "platinum" ->
                if (pokemon.form == "sky") listOf("") else listOf("-l")
            pokemon.default ||
                pokemon.form in
                    setOf(
                        "",
                        "normal",
                        "land",
                        "male",
                        "standard",
                        "ordinary",
                        "incarnate",
                        "plant",
                        "west",
                        "red-striped",
                        "disguised",
                    ) -> listOf("")
            pokemon.form == "mega-x" -> listOf("-mx", "-x", "-m-x")
            pokemon.form == "mega-y" -> listOf("-my", "-y", "-m-y")
            pokemon.form == "mega" -> listOf("-m", "-mega")
            pokemon.form == "alola" -> listOf("-a", "-alola")
            pokemon.form == "galar" -> listOf("-g", "-galar")
            pokemon.form == "hisui" -> listOf("-h", "-hisui")
            pokemon.form == "paldea" -> listOf("-p", "-paldea")
            pokemon.form == "sky" -> listOf("-s")
            pokemon.form == "female" -> listOf("-f")
            else -> listOf("-${pokemon.form}")
        }
    val numbers =
        listOf(pokemon.number.toString().padStart(3, '0'), pokemon.number.toString()).distinct()
    return directories.flatMap { directory ->
        numbers.flatMap { number ->
            suffixes.map { suffix -> "https://www.serebii.net/Shiny/$directory/$number$suffix.png" }
        }
    }
}

fun huntSystem(version: String): String =
    when (version) {
        "gold",
        "silver",
        "crystal" -> "Game Boy Color"
        "ruby-sapphire",
        "emerald",
        "firered-leafgreen" -> "Game Boy Advance"
        "diamond-pearl",
        "platinum",
        "heartgold-soulsilver",
        "black-white",
        "black-2-white-2" -> "Nintendo DS"
        "x-y",
        "omegaruby-alphasapphire",
        "sun-moon",
        "ultra-sun-ultra-moon" -> "Nintendo 3DS"
        "legends-z-a" -> "Nintendo Switch 2"
        else -> "Nintendo Switch"
    }
