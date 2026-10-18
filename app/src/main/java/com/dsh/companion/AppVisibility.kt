package com.dsh.companion

import kotlinx.coroutines.flow.MutableStateFlow

/** Tracks whether the main activity is in the foreground, so the overlay can hide itself. */
object AppVisibility {
    val foreground = MutableStateFlow(false)
}
