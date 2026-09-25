package com.ppkitchen.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeState {
    var mode by mutableStateOf("system")
        private set

    fun update(newMode: String) {
        mode = newMode
    }
}