package com.cmyksoda.savepoint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val HuntConfigSaver =
    listSaver<HuntConfig, Any>(
        save = {
            listOf(
                it.research,
                it.chain,
                it.sparklingPower,
                it.lure,
                it.searchLevel,
                it.battled,
                it.diglett,
                it.radarMusic,
            )
        },
        restore = {
            HuntConfig(
                it[0] as Int,
                it[1] as Int,
                it[2] as Int,
                it[3] as Boolean,
                it[4] as Int,
                it[5] as Int,
                it[6] as Boolean,
                it[7] as Boolean,
            )
        },
    )

@Composable
private fun HuntToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AeroCheckbox(checked, null)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HuntNumber(
    label: String,
    value: Int,
    maximum: Int = Int.MAX_VALUE,
    onChange: (Int) -> Unit,
) {
    var text by rememberSaveable(label) { mutableStateOf(value.toString()) }
    AeroTextField(
        text,
        { next ->
            if (
                next.isEmpty() ||
                    next.all { it.isDigit() } && next.toIntOrNull()?.let { it <= maximum } == true
            ) {
                text = next
                onChange(next.toIntOrNull() ?: 0)
            }
        },
        label = label,
        singleLine = true,
        keyboardType = KeyboardType.Number,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun HuntModifiers(
    version: String,
    method: String,
    charm: Boolean,
    config: HuntConfig,
    pokemon: String = "",
    onCharm: (Boolean) -> Unit,
    onConfig: (HuntConfig) -> Unit,
) {
    val family = huntVersionFamily(version)
    val research = family == "legends-arceus"
    val chain =
        method in setOf("Poké Radar", "Chain fishing", "SOS calls", "Catch combo", "DexNav") ||
            (method == "Mass outbreaks" && family == "scarlet-violet")
    val sparkling = hasSparklingPower(version, method)
    val showCharm = charmApplies(version, method, pokemon)
    val note = huntMechanicNote(version, method)
    if (
        research ||
            chain ||
            sparkling ||
            showCharm ||
            method in setOf("Brilliant encounters", "Grand Underground") ||
            note != null
    ) {
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hunt settings", style = MaterialTheme.typography.titleSmall)
                if (showCharm)
                    HuntToggle("Shiny charm", charm) { checked ->
                        if (research && config.research == 0)
                            onConfig(config.copy(research = 1))
                        onCharm(checked)
                    }
                if (research) {
                    val researchNames = listOf("Not complete", "Research level 10", "Perfect entry")
                    Choice(
                        "Pokédex research · this Pokémon",
                        researchNames[maxOf(config.research, if (charm) 1 else 0)],
                        if (charm) researchNames.drop(1) else researchNames,
                    ) {
                        onConfig(config.copy(research = researchNames.indexOf(it)))
                    }
                }
                key(version, method) {
                    if (chain)
                        HuntNumber(
                            when (method) {
                                "Catch combo" -> "Catch combo"
                                "Chain fishing" -> "Consecutive hooks"
                                "Mass outbreaks" -> "Caught or defeated in this outbreak"
                                "DexNav" -> "Chain · already caught or defeated"
                                else -> "Chain length"
                            },
                            config.chain,
                        ) {
                            onConfig(config.copy(chain = it))
                        }
                    if (method == "DexNav")
                        HuntNumber("DexNav search level · 0–999", config.searchLevel, 999) {
                            onConfig(config.copy(searchLevel = it))
                        }
                    if (method == "Brilliant encounters")
                        HuntNumber("Number battled · Pokédex value", config.battled) {
                            onConfig(config.copy(battled = it))
                        }
                }
                if (sparkling)
                    Choice(
                        if (family == "legends-z-a") "Sparkling Power · donut"
                        else "Sparkling Power · meal",
                        listOf("None", "Level 1", "Level 2", "Level 3")[config.sparklingPower],
                        listOf("None", "Level 1", "Level 2", "Level 3"),
                    ) {
                        onConfig(
                            config.copy(
                                sparklingPower =
                                    listOf("None", "Level 1", "Level 2", "Level 3").indexOf(it)
                            )
                        )
                    }
                if (family == "lets-go" && method in setOf("Random encounters", "Catch combo"))
                    HuntToggle("Lure active", config.lure) { onConfig(config.copy(lure = it)) }
                if (method == "Grand Underground")
                    HuntToggle("40-Diglett bonus active", config.diglett) {
                        onConfig(config.copy(diglett = it))
                    }
                if (family == "x-y" && method == "Poké Radar")
                    HuntToggle("Upbeat Radar music", config.radarMusic) {
                        onConfig(config.copy(radarMusic = it))
                    }
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun HuntOddsPreview(game: Game) {
    val odds = calculatedOdds(game)
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "Shiny odds",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                odds.first?.let { "1 in ${formatHuntOdds(it)}" } ?: "Custom odds needed",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(odds.second, style = MaterialTheme.typography.bodySmall)
        }
    }
}
