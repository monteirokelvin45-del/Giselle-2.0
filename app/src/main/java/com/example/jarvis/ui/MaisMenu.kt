// ui/MaisMenu.kt — menu de três pontos: leva a Conversas, Cofre, Code, Artefatos e Definições.
package com.example.jarvis.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** Números das abas (ver JarvisApp). */
object Abas {
    const val INICIO = 0          // Conversas
    const val CONFIRMACOES = 1
    const val MEMORIAS = 2        // Cofre
    const val TRACKER = 3
    const val REGISTRAR = 4
    const val ATUALIZACOES = 5
    const val CODE = 6            // revisão de patches
    const val ARTEFATOS = 7       // pendências do módulo de negócio
    const val DEFINICOES = 8
}

private data class Opcao(val rotulo: String, val icone: ImageVector, val aba: Int)

private val OPCOES_MAIS = listOf(
    Opcao("Conversas", Icons.Filled.Forum, Abas.INICIO),
    Opcao("Cofre", Icons.Filled.Lock, Abas.MEMORIAS),
    Opcao("Code", Icons.Filled.Code, Abas.CODE),
    Opcao("Artefatos", Icons.Filled.Inventory2, Abas.ARTEFATOS),
    Opcao("Definições", Icons.Filled.Settings, Abas.DEFINICOES),
)

@Composable
fun MaisMenu(onEscolher: (Int) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aberto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            OPCOES_MAIS.forEach { op ->
                DropdownMenuItem(
                    text = { Text(op.rotulo) },
                    leadingIcon = { Icon(op.icone, contentDescription = null) },
                    onClick = { aberto = false; onEscolher(op.aba) },
                )
            }
        }
    }
}
