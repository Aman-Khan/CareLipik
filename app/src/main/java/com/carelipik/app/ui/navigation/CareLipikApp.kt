package com.carelipik.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carelipik.app.domain.model.ConsultationDestination
import com.carelipik.app.ui.screens.placeholder.PlaceholderScreen
import com.carelipik.app.ui.screens.welcome.WelcomeScreen
import com.carelipik.app.ui.screens.welcome.WelcomeViewModel

@Composable
fun CareLipikApp(welcomeViewModel: WelcomeViewModel = viewModel()) {
    val welcomeUiState by welcomeViewModel.uiState.collectAsState()
    val navigator = remember { CareLipikNavigator() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (navigator.currentDestination) {
            ConsultationDestination.Welcome -> WelcomeScreen(
                uiState = welcomeUiState,
                onConsentChanged = welcomeViewModel::setRecordingConsent,
                onContinue = navigator::navigateToNext,
                modifier = Modifier.padding(innerPadding)
            )
            else -> PlaceholderScreen(
                destination = navigator.currentDestination,
                onNext = navigator::navigateToNext,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
