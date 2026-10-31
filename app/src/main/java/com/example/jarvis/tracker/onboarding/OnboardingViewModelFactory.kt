// ============================================================
// tracker/onboarding/OnboardingViewModelFactory.kt
// ============================================================

package com.example.jarvis.tracker.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.jarvis.tracker.data.TrackerDatabase

class OnboardingViewModelFactory(private val db: TrackerDatabase) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OnboardingViewModel(
            onboardingStateDao = db.onboardingStateDao(),
            configPerfilDao = db.configPerfilDao(),
            metasDao = db.metasDao(),
            planoTreinoDao = db.planoTreinoDao()
        ) as T
    }
}
