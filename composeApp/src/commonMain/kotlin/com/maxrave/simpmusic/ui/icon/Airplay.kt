package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val SimpIcons.Airplay: ImageVector
  get() {
    if (_Airplay != null) {
      return _Airplay!!
    }
    _Airplay =
      ImageVector.Builder(
          name = "Airplay",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(7.8f, 21f)
            quadTo(7.45f, 21f, 7.25f, 20.76f)
            reflectiveQuadTo(7.05f, 20.25f)
            quadToRelative(0f, -0.13f, 0.05f, -0.26f)
            reflectiveQuadTo(7.28f, 19.73f)
            lineTo(11.3f, 15.7f)
            quadToRelative(0.15f, -0.15f, 0.32f, -0.22f)
            reflectiveQuadTo(12f, 15.4f)
            reflectiveQuadToRelative(0.38f, 0.07f)
            reflectiveQuadTo(12.7f, 15.7f)
            lineToRelative(4.03f, 4.03f)
            quadToRelative(0.13f, 0.13f, 0.18f, 0.26f)
            reflectiveQuadToRelative(0.05f, 0.26f)
            quadToRelative(0f, 0.27f, -0.2f, 0.51f)
            reflectiveQuadTo(16.2f, 21f)
            horizontalLineTo(7.8f)
            close()
            moveTo(4f, 3f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 5f)
            verticalLineTo(17f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 19f)
            horizontalLineTo(19.83f)
            quadToRelative(-0.4f, 0f, -0.76f, -0.15f)
            reflectiveQuadTo(18.43f, 18.43f)
            lineToRelative(-4.3f, -4.3f)
            quadTo(13.7f, 13.7f, 13.15f, 13.48f)
            reflectiveQuadTo(12f, 13.25f)
            reflectiveQuadToRelative(-1.15f, 0.22f)
            reflectiveQuadTo(9.88f, 14.13f)
            lineToRelative(-4.3f, 4.3f)
            quadTo(5.3f, 18.7f, 4.94f, 18.85f)
            quadTo(4.58f, 19f, 4.18f, 19f)
            horizontalLineTo(4f)
            quadTo(3.18f, 19f, 2.59f, 18.41f)
            reflectiveQuadTo(2f, 17f)
            verticalLineTo(5f)
            quadTo(2f, 4.17f, 2.59f, 3.59f)
            reflectiveQuadTo(4f, 3f)
            close()
          }
        }
        .build()
    return _Airplay!!
  }

private var _Airplay: ImageVector? = null
