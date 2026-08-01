package org.jellyfin.androidtv.flixfin.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors

/**
 * Text, with the project's defaults baked in.
 *
 * `BasicText` rather than Material's `Text` deliberately: this app has no
 * Material theme wired into the Compose tree, so `Text` would resolve its colour
 * and style from a default that is not ours. Passing every value explicitly is
 * the only way to be sure what ships.
 *
 * The shadow is on by default because almost every string in this UI sits over
 * artwork, and a title that is legible over a dark still and invisible over a
 * bright one is a bug that only shows up on some films.
 */
@Composable
fun FlixFinText(
	text: String,
	size: TextUnit,
	modifier: Modifier = Modifier,
	weight: FontWeight = FlixFinTheme.Type.Regular,
	color: androidx.compose.ui.graphics.Color = Colors.Text,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
	letterSpacing: TextUnit = 0.sp,
	shadow: Boolean = true,
) {
	BasicText(
		text = text,
		modifier = modifier,
		maxLines = maxLines,
		overflow = overflow,
		style = TextStyle(
			color = color,
			fontSize = size,
			fontWeight = weight,
			letterSpacing = letterSpacing,
			shadow = if (shadow) {
				Shadow(color = Colors.BackgroundDeep.copy(alpha = 0.9f), blurRadius = 8f)
			} else {
				null
			},
		),
	)
}
