package com.carelipik.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carelipik.app.domain.model.ConsultationDestination

/** Minimal route holder for the fixed, local consultation flow. */
class CareLipikNavigator {
    var currentDestination by mutableStateOf(ConsultationDestination.Welcome)
        private set

    fun navigateToNext() {
        val destinations = ConsultationDestination.entries
        val nextIndex = (destinations.indexOf(currentDestination) + 1) % destinations.size
        currentDestination = destinations[nextIndex]
    }
}
