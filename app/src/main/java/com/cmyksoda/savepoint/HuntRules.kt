package com.cmyksoda.savepoint

import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.expm1
import kotlin.math.ln1p
import kotlin.math.pow

val huntGameEditions =
    linkedMapOf(
        "gold" to "Gold",
        "silver" to "Silver",
        "crystal" to "Crystal",
        "ruby" to "Ruby",
        "sapphire" to "Sapphire",
        "emerald" to "Emerald",
        "firered" to "FireRed",
        "leafgreen" to "LeafGreen",
        "diamond" to "Diamond",
        "pearl" to "Pearl",
        "platinum" to "Platinum",
        "heartgold" to "HeartGold",
        "soulsilver" to "SoulSilver",
        "black" to "Black",
        "white" to "White",
        "black-2" to "Black 2",
        "white-2" to "White 2",
        "x" to "X",
        "y" to "Y",
        "omega-ruby" to "Omega Ruby",
        "alpha-sapphire" to "Alpha Sapphire",
        "sun" to "Sun",
        "moon" to "Moon",
        "ultra-sun" to "Ultra Sun",
        "ultra-moon" to "Ultra Moon",
        "lets-go-pikachu" to "Let's Go Pikachu",
        "lets-go-eevee" to "Let's Go Eevee",
        "sword" to "Sword",
        "shield" to "Shield",
        "brilliant-diamond" to "Brilliant Diamond",
        "shining-pearl" to "Shining Pearl",
        "legends-arceus" to "Legends: Arceus",
        "scarlet" to "Scarlet",
        "violet" to "Violet",
        "legends-z-a" to "Legends: Z-A",
    )

private val huntPairs =
    linkedMapOf(
        "ruby-sapphire" to listOf("ruby", "sapphire"),
        "firered-leafgreen" to listOf("firered", "leafgreen"),
        "diamond-pearl" to listOf("diamond", "pearl"),
        "heartgold-soulsilver" to listOf("heartgold", "soulsilver"),
        "black-white" to listOf("black", "white"),
        "black-2-white-2" to listOf("black-2", "white-2"),
        "x-y" to listOf("x", "y"),
        "omegaruby-alphasapphire" to listOf("omega-ruby", "alpha-sapphire"),
        "sun-moon" to listOf("sun", "moon"),
        "ultra-sun-ultra-moon" to listOf("ultra-sun", "ultra-moon"),
        "lets-go" to listOf("lets-go-pikachu", "lets-go-eevee"),
        "sword-shield" to listOf("sword", "shield"),
        "brilliant-diamond-shining-pearl" to listOf("brilliant-diamond", "shining-pearl"),
        "scarlet-violet" to listOf("scarlet", "violet"),
    )

val huntVersions: Map<String, String> =
    huntGameEditions +
        huntPairs.mapValues { (_, editions) ->
            editions.joinToString(" / ") { huntGameEditions.getValue(it) }
        } +
        ("other" to "Other / custom game")

fun huntVersionMembers(version: String): List<String> = huntPairs[version] ?: listOf(version)

fun huntVersionFamily(version: String): String =
    huntPairs.entries.firstOrNull { version in it.value }?.key ?: version

fun baseShinyOdds(version: String): Int? =
    when (huntVersionFamily(version)) {
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
        "black-2-white-2" -> 8192
        else -> if (version in huntVersions && version != "other") 4096 else null
    }

data class HuntConfig(
    val research: Int = 0,
    val chain: Int = 0,
    val sparklingPower: Int = 0,
    val lure: Boolean = false,
    val searchLevel: Int = 0,
    val battled: Int = 0,
    val diglett: Boolean = false,
    val radarMusic: Boolean = false,
)

fun hasShinyCharm(version: String): Boolean =
    huntVersionFamily(version) in
        setOf(
            "black-2-white-2",
            "x-y",
            "omegaruby-alphasapphire",
            "sun-moon",
            "ultra-sun-ultra-moon",
            "lets-go",
            "sword-shield",
            "brilliant-diamond-shining-pearl",
            "legends-arceus",
            "scarlet-violet",
            "legends-z-a",
        )

fun charmApplies(version: String, method: String, pokemon: String = ""): Boolean {
    if (!hasShinyCharm(version)) return false
    if (
        method in
            setOf(
                "Poké Radar",
                "Max Raid Battles",
                "Tera Raid Battles",
                "Gift Pokémon",
                "Fossil revival",
                "Fixed symbol encounters",
            )
    )
        return false
    val family = huntVersionFamily(version)
    if (family == "brilliant-diamond-shining-pearl")
        return method in setOf("Breeding", "Masuda method")
    if (family == "scarlet-violet" && method == "Soft resets") return false
    if (
        family == "sword-shield" &&
            method in setOf("Soft resets", "Runaways") &&
            pokemon in
                setOf("regirock", "regice", "registeel", "regieleki", "regidrago", "regigigas")
    )
        return false
    return method in huntMethods(version)
}

fun hasSparklingPower(version: String, method: String): Boolean =
    when (huntVersionFamily(version)) {
        "scarlet-violet" ->
            method in setOf("Random encounters", "Runaways", "Mass outbreaks", "Sparkling Power")
        "legends-z-a" -> method == "Hyperspace encounters"
        else -> false
    }

fun huntMethods(version: String): List<String> {
    val family = huntVersionFamily(version)
    if (family == "other" || version.isBlank()) return emptyList()
    if (family == "legends-arceus")
        return listOf(
            "Random encounters",
            "Mass outbreaks",
            "Massive mass outbreaks",
            "Space-time distortions",
        )
    if (family == "legends-z-a")
        return listOf("Random encounters", "Hyperspace encounters", "Fossil revival")
    val result = mutableListOf("Random encounters", "Soft resets", "Runaways", "Gift Pokémon")
    if (family != "lets-go") result += "Breeding"
    if (
        family !in
            setOf(
                "gold",
                "silver",
                "crystal",
                "ruby-sapphire",
                "emerald",
                "firered-leafgreen",
                "lets-go",
            )
    )
        result += "Masuda method"
    when (family) {
        "diamond-pearl",
        "platinum",
        "x-y",
        "brilliant-diamond-shining-pearl" -> result += "Poké Radar"
        "omegaruby-alphasapphire" -> result += "DexNav"
        "sun-moon",
        "ultra-sun-ultra-moon" -> result += "SOS calls"
        "lets-go" -> result += "Catch combo"
        "sword-shield" ->
            result += listOf("Dynamax Adventures", "Brilliant encounters", "Max Raid Battles")
        "scarlet-violet" ->
            result += listOf("Mass outbreaks", "Tera Raid Battles", "Fixed symbol encounters")
    }
    if (family in setOf("x-y", "omegaruby-alphasapphire")) result += "Chain fishing"
    if (family == "x-y") result += "Friend Safari"
    if (family == "brilliant-diamond-shining-pearl") result += "Grand Underground"
    return result
}

private fun rolledDenominator(base: Int, rolls: Int): Double =
    1.0 / -expm1(rolls * ln1p(-1.0 / base))

fun calculatedOdds(game: Game): Pair<Double?, String> {
    game.manualOdds?.let {
        return it.toDouble() to "manual odds"
    }
    val base =
        baseShinyOdds(game.pokemonVersion) ?: return null to "enter odds for this custom hunt"
    val family = huntVersionFamily(game.pokemonVersion)
    val method = game.method
    if (method !in huntMethods(game.pokemonVersion))
        return null to "enter odds for this custom method"
    val config = game.huntConfig
    val charm = game.shinyCharm && charmApplies(game.pokemonVersion, method, game.pokemon)
    val extra = if (charm) 2 else 0
    fun rolls(count: Int, label: String) = rolledDenominator(base, count) to label

    if (family == "legends-arceus") {
        val research = maxOf(config.research, if (charm) 1 else 0)
        val researchRolls =
            when (research) {
                2 -> 3
                1 -> 1
                else -> 0
            }
        val outbreak =
            when (method) {
                "Mass outbreaks" -> 25
                "Massive mass outbreaks" -> 12
                else -> 0
            }
        return rolls(
            1 + researchRolls + outbreak + if (charm) 3 else 0,
            "research + encounter bonuses",
        )
    }

    if (family == "legends-z-a") {
        if (method == "Fossil revival") return 4096.0 to "fossils use base odds"
        val power =
            if (method == "Hyperspace encounters") config.sparklingPower.coerceIn(0, 3) else 0
        return rolls(
            1 + power + if (charm) 3 else 0,
            "${1 + power + if (charm) 3 else 0} shiny rolls per spawn",
        )
    }

    return when (method) {
        "Masuda method" ->
            rolls(
                (if (family in setOf("diamond-pearl", "platinum", "heartgold-soulsilver")) 5
                else 6) + extra,
                "different-language parents",
            )
        "Breeding" ->
            if (family in setOf("gold", "silver", "crystal"))
                null to
                    "Gen II egg odds depend on the parents’ DVs and offspring gender; enter your odds"
            else rolls(if (charm && base == 4096) 2 else 1 + extra, "per egg")
        "Dynamax Adventures" -> (if (charm) 100.0 else 300.0) to "per Pokémon on the results screen"
        "Friend Safari" -> rolls(5 + extra, "Friend Safari shiny rolls")
        "Chain fishing" -> rolls(1 + 2 * config.chain.coerceIn(0, 20) + extra, "per hooked Pokémon")
        "SOS calls" -> {
            val chain = if (family == "sun-moon") config.chain % 256 else config.chain
            rolls(
                1 +
                    when {
                        chain >= 31 -> 12
                        chain >= 21 -> 8
                        chain >= 11 -> 4
                        else -> 0
                    } +
                    extra,
                "per called ally",
            )
        }

        "Catch combo" ->
            rolls(
                1 +
                    when {
                        config.chain >= 31 -> 11
                        config.chain >= 21 -> 7
                        config.chain >= 11 -> 3
                        else -> 0
                    } +
                    extra +
                    if (config.lure) 1 else 0,
                "next spawn of the chained species",
            )

        "Brilliant encounters" ->
            rolls(
                1 +
                    when {
                        config.battled >= 500 -> 6
                        config.battled >= 300 -> 5
                        config.battled >= 200 -> 4
                        config.battled >= 100 -> 3
                        config.battled >= 50 -> 2
                        config.battled >= 1 -> 1
                        else -> 0
                    } +
                    extra,
                "Brilliant Aura encounters only",
            )

        "Mass outbreaks" ->
            rolls(
                1 +
                    when {
                        config.chain >= 60 -> 2
                        config.chain >= 30 -> 1
                        else -> 0
                    } +
                    extra +
                    config.sparklingPower.coerceIn(0, 3),
                "current outbreak · matching meal type",
            )

        "Grand Underground" ->
            rolls(
                if (config.diglett) 2 else 1,
                "${if (config.diglett) "40-Diglett bonus active" else "no Diglett bonus"} · charm does not apply",
            )
        "Max Raid Battles" ->
            base.toDouble() to "standard raid · event raids may use different odds"
        "Tera Raid Battles" ->
            (4294967296.0 / 1046775) to "standard raid · event raids may use different odds"
        "Fixed symbol encounters" ->
            base.toDouble() to "fixed symbol · charm and meal powers do not apply"
        "Poké Radar" -> radarOdds(family, config)
        "DexNav" -> dexNavOdds(config, charm)
        "Gift Pokémon",
        "Fossil revival" -> base.toDouble() to "gift / revival · charm does not apply"
        else -> {
            val power =
                if (hasSparklingPower(game.pokemonVersion, method))
                    config.sparklingPower.coerceIn(0, 3)
                else 0
            val lure =
                if (family == "lets-go" && method == "Random encounters" && config.lure) 1 else 0
            rolls(
                1 + extra + power + lure,
                if (power + lure > 0) "active spawn bonuses"
                else if (charm) "shiny charm" else "standard base odds",
            )
        }
    }
}

private fun radarOdds(family: String, config: HuntConfig): Pair<Double?, String> {
    val chain = config.chain.coerceIn(0, 40)
    if (family == "x-y") {
        if (chain == 0 && !config.radarMusic) return null to "start a chain to roll shiny patches"
        return (if (config.radarMusic) 100.0 else 8100.0 - chain * 200) to
            "per rapidly shaking patch · no charm bonus"
    }
    if (family == "brilliant-diamond-shining-pearl") {
        val threshold =
            when (chain) {
                in 0..29 -> 16 + chain
                in 30..35 -> 20 + chain
                36 -> 66
                37 -> 82
                38 -> 164
                39 -> 328
                else -> 656
            }
        return 65536.0 / threshold to "per grass patch · no charm bonus"
    }
    if (chain == 0) return null to "start a chain to roll shiny patches"
    return 65536.0 / ceil(65535.0 / (8200 - chain * 200)) to "per grass patch"
}

private fun dexNavOdds(config: HuntConfig, charm: Boolean): Pair<Double, String> {
    val search = config.searchLevel.coerceIn(0, 999)
    val target =
        when {
            search > 200 -> search + 600
            search > 100 -> search * 2 + 400
            else -> search * 6
        }
    val force = ceil(target / 100.0) / 10000.0
    val next = config.chain % 100 + 1
    val checks =
        (if (charm) 3 else 1) +
            when (next) {
                50 -> 5
                100 -> 10
                else -> 0
            }
    val naturalMiss = (4095.0 / 4096).pow(if (charm) 3 else 1)
    fun chance(boost: Int) = 1.0 - naturalMiss * (1.0 - force).pow(checks + boost)
    val probability =
        if (config.chain > 0 && next % 5 == 0) chance(4) else .96 * chance(0) + .04 * chance(4)
    return 1.0 / probability to "next DexNav spawn · random boost included"
}

fun formatHuntOdds(denominator: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(denominator)

fun huntMechanicNote(version: String, method: String): String? =
    when {
        huntVersionFamily(version) == "legends-arceus" ->
            "Research is for this Pokémon’s entry. The charm requires research level 10 across the Pokédex. Existing overworld spawns keep their original shiny roll."
        huntVersionFamily(version) == "legends-z-a" && method == "Hyperspace encounters" ->
            "Mega Dimension DLC. Sparkling Power must match the Pokémon’s type. Level 3 also guarantees one matching-type shiny on entry; the odds shown apply to the other spawns."
        method == "Poké Radar" ->
            "Count individual grass patches, not Radar uses. A sparkling patch guarantees a shiny. Keep chain length separate from your encounter total."
        method == "Catch combo" ->
            "The combo bonus applies only to the next spawn of the chained species after each catch. Keep catching to renew it."
        method == "SOS calls" && huntVersionFamily(version) == "sun-moon" ->
            "The original Sun / Moon chain counter wraps after 255. Ultra Sun / Ultra Moon keeps its bonus."
        method == "Brilliant encounters" ->
            "Only count Brilliant Aura Pokémon at these boosted odds; ordinary spawns keep their normal rate."
        method == "DexNav" ->
            "Search level is the DexNav value for this species. Chain is the number already caught or defeated; the next spawn receives the calculated odds."
        method == "Mass outbreaks" ->
            "Progress counts Pokémon caught or defeated in this outbreak, separately from your total encounters. Event outbreaks can have special rates; use an odds override for those."
        huntVersionFamily(version) == "brilliant-diamond-shining-pearl" &&
            method !in setOf("Breeding", "Masuda method") ->
            "In Brilliant Diamond / Shining Pearl, the shiny charm only affects eggs."
        else -> null
    }

fun huntModifierSummary(game: Game): List<String> = buildList {
    val config = game.huntConfig
    val family = huntVersionFamily(game.pokemonVersion)
    if (family == "legends-arceus")
        add(
            when (maxOf(config.research, if (game.shinyCharm) 1 else 0)) {
                2 -> "perfect Pokédex entry"
                1 -> "research level 10"
                else -> "Pokédex entry not complete"
            }
        )
    if (game.method == "Mass outbreaks" && family == "scarlet-violet")
        add("${config.chain} caught or defeated in this outbreak")
    else if (
        game.method in setOf("Poké Radar", "Chain fishing", "SOS calls", "Catch combo", "DexNav")
    )
        add("chain: ${config.chain}")
    if (hasSparklingPower(game.pokemonVersion, game.method) && config.sparklingPower > 0)
        add("Sparkling Power level ${config.sparklingPower}")
    if (game.method == "DexNav") add("search level: ${config.searchLevel}")
    if (game.method == "Brilliant encounters") add("number battled: ${config.battled}")
    if (
        family == "lets-go" &&
            config.lure &&
            game.method in setOf("Random encounters", "Catch combo")
    )
        add("lure active")
    if (game.method == "Grand Underground" && config.diglett) add("40-Diglett bonus active")
    if (family == "x-y" && game.method == "Poké Radar" && config.radarMusic)
        add("upbeat Radar music")
}
