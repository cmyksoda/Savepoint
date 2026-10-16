package com.cmyksoda.savepoint

import org.json.JSONObject

class HuntCatalogIndex(
    val choices: List<PokemonChoice>,
    private val encounters: Map<String, Map<String, Map<String, List<String>>>>,
) {
    private val bySlug = choices.associateBy { it.slug }

    private fun choice(slug: String): PokemonChoice? =
        bySlug[slug] ?: choices.firstOrNull { it.species == slug && it.default }

    private fun key(slug: String): String? =
        choice(slug)?.let { pokemon -> pokemon.huntForm?.let { "${pokemon.number}:$it" } }

    fun games(slug: String): List<String> =
        huntGameEditions.keys.filter {
            it in encounters[key(slug)].orEmpty() &&
                (choice(slug)?.gameFilter.orEmpty().isEmpty() ||
                    it in choice(slug)?.gameFilter.orEmpty())
        }

    fun methods(slug: String, version: String): List<String> {
        if (huntVersionMembers(version).none { it in games(slug) }) return emptyList()
        val supported =
            huntVersionMembers(version)
                .flatMap { encounters[key(slug)]?.get(it)?.keys.orEmpty() }
                .toSet()
        return huntMethods(version).filter {
            it in supported || (it == "Masuda method" && "Breeding" in supported)
        }
    }

    fun targets(slug: String, version: String, method: String): List<String> {
        val name = if (method == "Masuda method") "Breeding" else method
        val targets =
            huntVersionMembers(version)
                .flatMap { encounters[key(slug)]?.get(it)?.get(name).orEmpty() }
                .distinct()
        if (key(slug) in targets && choice(slug)?.form?.contains("gmax") != true) return emptyList()
        return targets
            .mapNotNull { target ->
                val number = target.substringBefore(':').toInt()
                val form = target.substringAfter(':').toInt()
                val exact = choices.firstOrNull { it.number == number && it.huntForm == form }
                (exact ?: choices.firstOrNull { it.number == number && it.default })?.label
            }
            .distinct()
    }

    fun supports(slug: String, version: String): Boolean = methods(slug, version).isNotEmpty()

    companion object {
        fun parse(json: String): HuntCatalogIndex {
            val root = JSONObject(json)
            val items = root.getJSONArray("pokemon")
            val choices =
                (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    PokemonChoice(
                        item.getString("slug"),
                        item.getString("species"),
                        item.getInt("number"),
                        item.getString("form"),
                        item.getBoolean("default"),
                        if (item.isNull("huntForm")) null else item.getInt("huntForm"),
                        item
                            .optJSONArray("gameFilter")
                            ?.let { filter ->
                                (0 until filter.length()).map { filter.getString(it) }.toSet()
                            }
                            .orEmpty(),
                    )
                }
            val data = root.getJSONObject("encounters")
            val encounters =
                data.keys().asSequence().associateWith { key ->
                    val games = data.getJSONObject(key)
                    games.keys().asSequence().associateWith { version ->
                        val methods = games.getJSONObject(version)
                        methods.keys().asSequence().associateWith { method ->
                            val targets = methods.getJSONArray(method)
                            (0 until targets.length()).map { targets.getString(it) }
                        }
                    }
                }
            return HuntCatalogIndex(choices, encounters)
        }
    }
}
