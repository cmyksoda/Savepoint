package com.cmyksoda.savepoint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.ln1p

fun displayPokemonName(slug: String): String =
    slug.trim().split(Regex("[-\\s]+")).joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
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
    val match =
        names.entries.firstOrNull { (_, aliases) -> aliases.any { name.contains(it) } }
            ?: return null
    if (match.key == "lets-go")
        return when {
            name.contains("pikachu") && !name.contains("eevee") -> "lets-go-pikachu"
            name.contains("eevee") && !name.contains("pikachu") -> "lets-go-eevee"
            else -> null
        }
    val editions = huntVersionMembers(match.key)
    if (editions.size == 1) return match.key
    val matches =
        match.value.mapIndexedNotNull { index, alias ->
            editions[index].takeIf { name.contains(alias) }
        }
    return matches.singleOrNull()
}

data class HuntProbability(
    val chance: Double,
    val oddsMultiples: Double,
    val encounters50: Long,
    val encounters90: Long,
    val progress90: Double,
)

fun huntProbability(encounters: Long, denominator: Int): HuntProbability =
    huntProbability(encounters, denominator.toDouble())

fun huntProbability(encounters: Long, denominator: Double): HuntProbability {
    require(encounters >= 0 && denominator.isFinite() && denominator >= 1)
    val logMiss = ln1p(-1.0 / denominator)
    fun threshold(chance: Double): Long =
        if (denominator == 1.0) 1 else ceil(ln(1.0 - chance) / logMiss).toLong()
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
        AeroProgress(stats.progress90.toFloat())
        val chance = if (stats.chance >= .9999) "≥99.99%" else "${formatHours(stats.chance * 100)}%"
        listOf(
                listOf(
                    "Chance at these settings" to chance,
                    "Times over odds" to "${formatHours(stats.oddsMultiples)}×",
                ),
                listOf(
                    "50% chance at" to NumberFormat.getIntegerInstance().format(stats.encounters50),
                    "90% chance at" to NumberFormat.getIntegerInstance().format(stats.encounters90),
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
            "Assumes every counted encounter used these settings. Earlier chain or bonus changes aren’t included; a shiny is never guaranteed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
