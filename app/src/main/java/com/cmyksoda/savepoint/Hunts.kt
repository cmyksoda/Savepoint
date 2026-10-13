package com.cmyksoda.savepoint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.ln1p
import org.json.JSONObject

fun displayPokemonName(slug: String): String =
    slug.trim().split(Regex("[-\\s]+")).joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }

fun resolvePokemon(slug: String, fetch: (String) -> JSONObject): JSONObject {
    return try {
        fetch("https://pokeapi.co/api/v2/pokemon/$slug")
    } catch (e: SourceUnavailable) {
        if (e.status != 404) throw e
        val species = fetch("https://pokeapi.co/api/v2/pokemon-species/$slug")
        val varieties = species.getJSONArray("varieties")
        val name =
            (0 until varieties.length())
                .map { varieties.getJSONObject(it) }
                .firstOrNull { it.optBoolean("is_default") }
                ?.getJSONObject("pokemon")
                ?.getString("name")
                ?: throw IllegalArgumentException(
                    "No default form was found. Enter a specific Pokémon form."
                )
        require(name.matches(Regex("[a-z0-9-]+"))) { "Invalid Pokémon form" }
        fetch("https://pokeapi.co/api/v2/pokemon/$name")
    }
}

fun inferHuntVersion(title: String): String? {
    val name = title.lowercase().replace("é", "e").replace(Regex("[^a-z0-9]"), "")
    val names =
        linkedMapOf(
            "legends-z-a" to listOf("legendsza"),
            "legends-arceus" to listOf("legendsarceus"),
            "brilliant-diamond-shining-pearl" to listOf("brilliantdiamond", "shiningpearl"),
            "omegaruby-alphasapphire" to listOf("omegaruby", "alphasapphire"),
            "ultra-sun-ultra-moon" to listOf("ultrasun", "ultramoon"),
            "black-2-white-2" to listOf("black2", "white2"),
            "heartgold-soulsilver" to listOf("heartgold", "soulsilver"),
            "firered-leafgreen" to listOf("firered", "leafgreen"),
            "lets-go" to listOf("letsgo"),
            "scarlet-violet" to listOf("scarlet", "violet"),
            "sword-shield" to listOf("sword", "shield"),
            "platinum" to listOf("platinum"),
            "diamond-pearl" to listOf("diamond", "pearl"),
            "black-white" to listOf("black", "white"),
            "ruby-sapphire" to listOf("ruby", "sapphire"),
            "emerald" to listOf("emerald"),
            "crystal" to listOf("crystal"),
            "gold" to listOf("gold"),
            "silver" to listOf("silver"),
            "sun-moon" to listOf("pokemonsun", "pokemonmoon"),
            "x-y" to listOf("pokemonx", "pokemony"),
        )
    return names.entries.firstOrNull { (_, aliases) -> aliases.any { name.contains(it) } }?.key
}

private val olderHuntVersions =
    setOf(
        "gold",
        "silver",
        "crystal",
        "ruby-sapphire",
        "emerald",
        "firered-leafgreen",
        "diamond-pearl",
        "platinum",
        "heartgold-soulsilver",
        "black-white",
        "black-2-white-2",
    )

fun baseShinyOdds(version: String): Int? =
    when {
        version in olderHuntVersions -> 8192
        version in huntVersions && version != "other" -> 4096
        else -> null
    }

fun huntMethods(version: String): List<String> {
    val methods = mutableListOf("Random encounters", "Soft resets", "Runaways", "Breeding")
    if (
        version !in
            setOf(
                "gold",
                "silver",
                "crystal",
                "ruby-sapphire",
                "emerald",
                "firered-leafgreen",
                "lets-go",
                "legends-arceus",
                "legends-z-a",
                "other",
            )
    )
        methods += "Masuda method"
    when (version) {
        "diamond-pearl",
        "platinum",
        "x-y",
        "brilliant-diamond-shining-pearl" -> methods += "Poké Radar"
        "omegaruby-alphasapphire" -> methods += "DexNav"
        "sun-moon",
        "ultra-sun-ultra-moon" -> methods += "SOS calls"
        "lets-go" -> methods += "Catch combo"
        "sword-shield" -> methods += listOf("Dynamax Adventures", "Brilliant encounters")
        "legends-arceus" -> methods += listOf("Mass outbreaks", "Massive mass outbreaks")
        "scarlet-violet" -> methods += listOf("Mass outbreaks", "Sparkling Power")
    }
    if (version in setOf("x-y", "omegaruby-alphasapphire")) methods += "Chain fishing"
    if (version == "x-y") methods += "Friend Safari"
    return methods
}

data class HuntProbability(
    val chance: Double,
    val oddsMultiples: Double,
    val encounters50: Long,
    val encounters90: Long,
    val progress90: Double,
)

fun huntProbability(encounters: Long, denominator: Int): HuntProbability {
    require(encounters >= 0 && denominator > 0)
    val logMiss = ln1p(-1.0 / denominator)
    fun threshold(chance: Double): Long =
        if (denominator == 1) 1 else ceil(ln(1.0 - chance) / logMiss).toLong()
    val at90 = threshold(.9)
    return HuntProbability(
        chance = if (encounters == 0L) 0.0 else -expm1(encounters * logMiss),
        oddsMultiples = encounters.toDouble() / denominator,
        encounters50 = threshold(.5),
        encounters90 = at90,
        progress90 = (encounters.toDouble() / at90).coerceIn(0.0, 1.0),
    )
}

@Composable
fun HuntStatistics(game: Game) {
    val denominator = calculatedOdds(game).first ?: return
    val stats = huntProbability(game.encounters, denominator)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
        Text(
            "${formatHours(stats.progress90 * 100)}% of the 90% threshold",
            style = MaterialTheme.typography.labelLarge,
        )
        LinearProgressIndicator(
            progress = { stats.progress90.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        val chance = if (stats.chance >= .9999) "≥99.99%" else "${formatHours(stats.chance * 100)}%"
        listOf(
                listOf(
                    "chance so far" to chance,
                    "times over odds" to "${formatHours(stats.oddsMultiples)}×",
                ),
                listOf(
                    "50% chance at" to "${stats.encounters50}",
                    "90% chance at" to "${stats.encounters90}",
                ),
            )
            .forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { (label, value) ->
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(value, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        Text(
            "Independent encounters at these odds; a shiny is never guaranteed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
