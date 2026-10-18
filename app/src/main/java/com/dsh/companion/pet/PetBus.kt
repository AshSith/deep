package com.dsh.companion.pet

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PetEvent {
    data class Say(val text: String) : PetEvent
    data class Clip(val name: String, val once: Boolean = true) : PetEvent
    data object Thinking : PetEvent
    data object Success : PetEvent
    data object Error : PetEvent
}

object PetBus {
    private val _events = MutableSharedFlow<PetEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun emit(event: PetEvent) {
        _events.tryEmit(event)
    }
}
