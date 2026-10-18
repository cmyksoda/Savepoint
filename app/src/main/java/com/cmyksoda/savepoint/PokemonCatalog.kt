package com.cmyksoda.savepoint

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PokemonChoice(
    val slug: String,
    val species: String,
    val number: Int,
    val form: String,
    val default: Boolean = form.isBlank(),
    val huntForm: Int? = null,
    val gameFilter: Set<String> = emptySet(),
    val sprite: Int? = null,
) {
    val label: String
        get() = displayPokemonName(slug)

    val spriteUrl: String?
        get() = sprite?.let { "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$it.png" }
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
    @Volatile private var memory: HuntCatalogIndex? = null

    suspend fun index(): HuntCatalogIndex =
        withContext(Dispatchers.IO) {
            memory
                ?: synchronized(this@PokemonCatalog) {
                    memory
                        ?: context.assets
                            .open("hunt-catalog.json")
                            .bufferedReader()
                            .use { HuntCatalogIndex.parse(it.readText()) }
                            .also { memory = it }
                }
        }

    suspend fun all(): List<PokemonChoice> = index().choices

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
        when (huntVersionFamily(version)) {
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
    when (huntVersionFamily(version)) {
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
