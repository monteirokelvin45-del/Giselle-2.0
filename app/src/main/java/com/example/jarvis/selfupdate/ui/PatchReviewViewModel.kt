// ============================================================
// selfupdate/ui/PatchReviewViewModel.kt
// ------------------------------------------------------------
// Implementação real de PatchReviewActions, ligando a tela ao
// SelfUpdateDatabase. Aprovar/rejeitar só muda status — quem
// decide gravar o arquivo em Downloads é a própria tela
// (exportarDiffParaDownloads), não este ViewModel.
// ============================================================

package com.example.jarvis.selfupdate.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jarvis.selfupdate.PatchStatus
import com.example.jarvis.selfupdate.PatchSuggestionEntity
import com.example.jarvis.selfupdate.SelfUpdateDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PatchReviewViewModel(context: Context) : ViewModel(), PatchReviewActions {

    private val dao = SelfUpdateDatabase.getInstance(context).patchSuggestionDao()

    override val pendentes: Flow<List<PatchSuggestionEntity>> =
        dao.porStatus(PatchStatus.PENDENTE.name)

    override suspend fun aprovar(patch: PatchSuggestionEntity) {
        dao.atualizarStatus(patch.id, PatchStatus.APROVADO.name, System.currentTimeMillis())
    }

    override suspend fun rejeitar(patch: PatchSuggestionEntity) {
        dao.atualizarStatus(patch.id, PatchStatus.REJEITADO.name, System.currentTimeMillis())
    }
}

class PatchReviewViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PatchReviewViewModel(context.applicationContext) as T
}
