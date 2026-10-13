package com.cmyksoda.savepoint

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

val Context.preferences by preferencesDataStore("savepoint")

data class Settings(
    val theme: String = "system",
    val sort: LibrarySort = LibrarySort.ADDED_NEW,
    val systemFilter: String = "",
    val collectionFilter: String = "",
    val collectionNames: Set<String> = emptySet(),
    val gridViews: Set<String> = emptySet(),
    val catalogUrl: String = "",
    val reminders: Boolean = false,
    val days: Set<Int> = (1..7).toSet(),
    val startMinute: Int = 1140,
    val endMinute: Int = 1260,
    val skipDate: String = "",
    val sentDate: String = "",
    val followupDate: String = "",
    val laterAt: Long = 0,
)

object JsonCodec {
    fun playthrough(p: Playthrough) =
        JSONObject().apply {
            put("id", p.id)
            put("finishedAt", p.finishedAt ?: JSONObject.NULL)
            put("feeling", p.feeling?.name ?: JSONObject.NULL)
            put("thoughts", p.thoughts)
            put("hours", p.hours ?: JSONObject.NULL)
        }

    fun playthrough(j: JSONObject) =
        Playthrough(
                id = j.getString("id"),
                finishedAt = if (j.isNull("finishedAt")) null else j.getLong("finishedAt"),
                feeling =
                    if (j.isNull("feeling")) null else Feeling.valueOf(j.getString("feeling")),
                thoughts = j.optString("thoughts"),
                hours = if (j.isNull("hours")) null else j.getDouble("hours"),
            )
            .also {
                require(it.id.isNotBlank() && (it.finishedAt == null || it.finishedAt >= 0)) {
                    "Invalid playthrough"
                }
                require(it.hours == null || it.hours.isFinite() && it.hours >= 0) {
                    "Invalid playtime"
                }
            }

    fun game(g: Game) =
        JSONObject().apply {
            put("id", g.id)
            put("title", g.title)
            put("system", g.system)
            put("shelf", g.shelf.name)
            put("place", g.place.name)
            put("position", g.position)
            put("added", g.added)
            put("release", g.release ?: JSONObject.NULL)
            put("cover", g.cover)
            put("coverProvider", g.coverProvider)
            put("source", g.source)
            put("sourceId", g.sourceId)
            put("notes", g.notes)
            put("pauseNote", g.pauseNote)
            put("collection", g.collection)
            put("collectionOrder", g.collectionOrder)
            put("pokemon", g.pokemon)
            put("pokemonVersion", g.pokemonVersion)
            put("sprite", g.sprite)
            put("method", g.method)
            put("encounters", g.encounters)
            put("shinyCharm", g.shinyCharm)
            put("manualOdds", g.manualOdds ?: JSONObject.NULL)
            put("playthroughs", JSONArray(g.playthroughs.map { playthrough(it) }))
            put("hasPlayed", g.hasPlayed)
            put("replayCount", g.replayCount ?: JSONObject.NULL)
        }

    fun game(j: JSONObject) =
        Game(
                id = j.getString("id"),
                title = j.getString("title"),
                system = j.getString("system"),
                shelf = Shelf.valueOf(j.getString("shelf")),
                place = Place.valueOf(j.getString("place")),
                position = j.getInt("position"),
                added = j.getLong("added"),
                release = if (j.isNull("release")) null else j.getLong("release"),
                cover = j.optString("cover"),
                coverProvider = j.optString("coverProvider"),
                source = j.optString("source", "manual"),
                sourceId = j.optString("sourceId"),
                notes = j.optString("notes"),
                pauseNote = j.optString("pauseNote"),
                collection = j.optString("collection"),
                collectionOrder = j.optInt("collectionOrder"),
                pokemon = j.optString("pokemon"),
                pokemonVersion = j.optString("pokemonVersion", "heartgold-soulsilver"),
                sprite = j.optString("sprite"),
                method = j.optString("method", "Random encounters"),
                encounters = j.optLong("encounters"),
                shinyCharm = j.optBoolean("shinyCharm"),
                manualOdds = if (j.isNull("manualOdds")) null else j.getInt("manualOdds"),
                playthroughs =
                    j.optJSONArray("playthroughs")?.let { entries ->
                        (0 until entries.length()).map { playthrough(entries.getJSONObject(it)) }
                    } ?: emptyList(),
                hasPlayed = j.optBoolean("hasPlayed", j.getString("place") == "FINISHED"),
                replayCount = if (j.isNull("replayCount")) null else j.getInt("replayCount"),
            )
            .also {
                require(it.id.isNotBlank() && it.title.isNotBlank()) { "Invalid game entry" }
                require(it.encounters >= 0 && (it.manualOdds == null || it.manualOdds > 0)) {
                    "Invalid hunt values"
                }
                require(it.replayCount == null || it.replayCount >= 0) { "Invalid replay count" }
                require(
                    it.playthroughs.map { entry -> entry.id }.distinct().size ==
                        it.playthroughs.size
                ) {
                    "Duplicate playthrough identifiers"
                }
            }

    fun settings(s: Settings) =
        JSONObject().apply {
            put("theme", s.theme)
            put("sort", s.sort.name)
            put("systemFilter", s.systemFilter)
            put("collectionFilter", s.collectionFilter)
            put("collectionNames", JSONArray(s.collectionNames.sorted()))
            put("gridViews", JSONArray(s.gridViews.sorted()))
            put("catalogUrl", s.catalogUrl)
            put("reminders", s.reminders)
            put("days", JSONArray(s.days.sorted()))
            put("startMinute", s.startMinute)
            put("endMinute", s.endMinute)
            put("skipDate", s.skipDate)
            put("sentDate", s.sentDate)
            put("followupDate", s.followupDate)
            put("laterAt", s.laterAt)
        }

    fun settings(j: JSONObject) =
        Settings(
                theme = j.optString("theme", "system"),
                sort = LibrarySort.valueOf(j.optString("sort", "ADDED_NEW")),
                systemFilter = j.optString("systemFilter"),
                collectionFilter = j.optString("collectionFilter"),
                collectionNames =
                    j.optJSONArray("collectionNames")?.let { a ->
                        (0 until a.length())
                            .map { a.getString(it) }
                            .filter { it.isNotBlank() }
                            .toSet()
                    } ?: emptySet(),
                gridViews =
                    j.optJSONArray("gridViews")?.let { a ->
                        (0 until a.length())
                            .map { a.getString(it) }
                            .filter { it in setOf("backlog", "paused", "log") }
                            .toSet()
                    } ?: emptySet(),
                catalogUrl = j.optString("catalogUrl"),
                reminders = j.optBoolean("reminders"),
                days =
                    j.optJSONArray("days")?.let { a ->
                        (0 until a.length()).map { a.getInt(it) }.toSet()
                    } ?: (1..7).toSet(),
                startMinute = j.optInt("startMinute", 1140),
                endMinute = j.optInt("endMinute", 1260),
                skipDate = j.optString("skipDate"),
                sentDate = j.optString("sentDate"),
                followupDate = j.optString("followupDate"),
                laterAt = j.optLong("laterAt"),
            )
            .also {
                require(it.theme in listOf("system", "light", "dark"))
                require(it.days.all { day -> day in 1..7 })
                require(
                    it.startMinute in 0..1439 &&
                        it.endMinute in 1..1440 &&
                        it.endMinute > it.startMinute
                )
            }
}

class Library(context: Context) {
    private val dao =
        Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db")
            .build()
            .games()
    private val mutex = Mutex()
    val games = MutableStateFlow<List<Game>>(emptyList())

    suspend fun load() =
        withContext(Dispatchers.IO) {
            games.value = dao.all().map { JsonCodec.game(JSONObject(it.payload)) }
        }

    suspend fun change(transform: (List<Game>) -> List<Game>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val changed = transform(games.value)
                dao.replace(
                    changed.map { g ->
                        GameRow().apply {
                            id = g.id
                            payload = JsonCodec.game(g).toString()
                        }
                    }
                )
                games.value = changed
            }
        }
}

class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("settings")
    val changes =
        context.preferences.data.map { preferences ->
            preferences[key]?.let { JsonCodec.settings(JSONObject(it)) } ?: Settings()
        }

    suspend fun read(): Settings =
        context.preferences.data.first()[key]?.let { JsonCodec.settings(JSONObject(it)) }
            ?: Settings()

    suspend fun change(transform: (Settings) -> Settings): Settings {
        var result = Settings()
        context.preferences.edit { p ->
            result = transform(p[key]?.let { JsonCodec.settings(JSONObject(it)) } ?: Settings())
            p[key] = JsonCodec.settings(result).toString()
        }
        return result
    }
}
