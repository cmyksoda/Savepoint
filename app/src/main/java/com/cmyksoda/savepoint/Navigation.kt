package com.cmyksoda.savepoint

import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

class ScreenHistory(saved: List<String> = listOf("home")) {
    private val trail = saved.ifEmpty { listOf("home") }.toMutableList()
    private val current = mutableStateOf(trail.last())
    val libraryReturn: String
        get() {
            val detail = trail.indexOf("details")
            return if (detail > 0) trail[detail - 1] else trail.first()
        }

    val savedRoutes: List<String>
        get() = trail.toList()

    val previous: String
        get() = trail.dropLast(1).lastOrNull() ?: "home"

    operator fun getValue(owner: Any?, property: KProperty<*>): String = current.value

    operator fun setValue(owner: Any?, property: KProperty<*>, destination: String) =
        open(destination)

    fun finishAdding(destination: String) {
        val add = trail.lastIndexOf("add")
        val hunt = trail.lastIndexOf("hunt-add")
        val origin = maxOf(add, hunt)
        if (origin >= 0) trail.subList(origin + 1, trail.size).clear()
        else if (trail.lastOrNull() in setOf("catalog", "form")) trail.removeAt(trail.lastIndex)
        current.value = trail.lastOrNull() ?: "home"
        open(destination)
    }

    fun open(destination: String) {
        if (destination == current.value) return
        if (destination in setOf("home", "backlog", "paused", "log")) {
            trail.clear()
            trail.add(destination)
        } else {
            val existing = trail.lastIndexOf(destination)
            if (existing >= 0) trail.subList(existing + 1, trail.size).clear()
            else trail.add(destination)
        }
        current.value = destination
    }
}
