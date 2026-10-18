package com.dsh.companion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class PersonaStore(context: Context) {
    private val file = File(context.filesDir, "personas.json")
    private val lock = Any()
    private val _personas = MutableStateFlow<List<Persona>>(emptyList())
    val personas: StateFlow<List<Persona>> = _personas.asStateFlow()

    init {
        val loaded = JsonStore.read<List<Persona>>(file, emptyList())
        if (loaded.isEmpty()) {
            _personas.value = listOf(Persona())
            persist(_personas.value)
        } else {
            _personas.value = loaded
        }
    }

    fun get(id: String?): Persona? = _personas.value.firstOrNull { it.id == id }

    fun first(): Persona? = _personas.value.firstOrNull()

    fun upsert(persona: Persona): Persona {
        synchronized(lock) {
            val next = _personas.value.toMutableList()
            val index = next.indexOfFirst { it.id == persona.id }
            if (index >= 0) next[index] = persona else next.add(persona)
            _personas.value = next
            persist(next)
        }
        return persona
    }

    fun delete(id: String) {
        synchronized(lock) {
            val next = _personas.value.filterNot { it.id == id }.toMutableList()
            if (next.isEmpty()) next.add(Persona())
            _personas.value = next
            persist(next)
        }
    }

    private fun persist(list: List<Persona>) {
        runCatching { JsonStore.write(file, list) }
    }
}
