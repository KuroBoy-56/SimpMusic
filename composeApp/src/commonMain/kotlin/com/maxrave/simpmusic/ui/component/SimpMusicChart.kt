package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Función neutralizada.
 * Se mantiene la firma de la función para no romper las pantallas que la importan
 * (como Explorar, Gráficas o Listas Locales), pero no dibuja absolutamente nada.
 * Esto elimina el anuncio de "Introducing SimpMusic Chart" de toda la app.
 */
@Composable
fun SimpMusicChartButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Vacío: El botón ya no existe visualmente.
}