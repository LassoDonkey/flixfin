package org.jellyfin.androidtv.flixfin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Dimens
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Motion
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type

/**
 * A browse card. 16:9 landscape, artwork-led.
 *
 * ## No border and no shadow at rest
 *
 * That is measured, not taste: in Prime Video's own 1080p frame the gap between
 * tiles samples `#00040D` at **100% of pixels** — there is no shadow falloff
 * around any card anywhere, and resting cards carry no border either
 * (visual-colour-type.md 2.2). On a near-black base, artwork is its own
 * elevation. The surface ramp is for things that are *not* artwork: buttons,
 * chips, dialogs, focus.
 *
 * ## The title is on the card, and that is a departure
 *
 * Netflix and Prime both carry zero typeset text on browse cards. The rule they
 * are actually following is narrower than "no text": label the card when the
 * artwork does not identify the item on its own. A personal library is full of
 * stills and thumbs with no title burned in, which is exactly that case — and a
 * photo of the live Prime app shows labels on every card in its "On now" and
 * "Your Live TV picks" rows for the same reason.
 *
 * ## Focus
 *
 * Scale, a 2dp ring, and nothing else. Apple's model: a focused item stands out
 * through "elevation to the foreground, illumination, and animation", and
 * "avoid using only color to indicate focus" (visual-colour-type.md 3.5). Depth
 * on a TV is communicated by behaviour, not by pixels. No drop shadow — see
 * above, nobody in the sample uses one.
 */
@Composable
fun FlixFinCard(
	title: String,
	imageUrl: String?,
	focused: Boolean,
	modifier: Modifier = Modifier,
	progress: Float? = null,
) {
	val scale by animateFloatAsState(
		targetValue = if (focused) Motion.FocusScale else 1f,
		animationSpec = tween(if (focused) Motion.FocusInMs else Motion.FocusOutMs),
		label = "cardScale",
	)

	Box(
		modifier = modifier
			.size(Dimens.CardWide, Dimens.CardWideHeight)
			.scale(scale)
			.clip(Dimens.TileRadius)
			.background(Colors.Surface)
			.then(
				// 2dp, not 1: a single dp disappears into TV overscan scaling and
				// the sharpening most sets apply on top of it.
				if (focused) Modifier.border(2.dp, Color.White, Dimens.TileRadius)
				else Modifier
			),
	) {
		AsyncImage(
			model = imageUrl,
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize(),
		)

		// A scrim only where the label sits. Tinting the whole card would dim the
		// one thing on screen doing the identifying.
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						0.56f to Color.Transparent,
						1f to Colors.BackgroundDeep.copy(alpha = 0.88f),
					)
				)
		)

		FlixFinText(
			text = title,
			size = Type.Chip,
			weight = Type.Bold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(start = 7.dp, end = 7.dp, bottom = 6.dp),
		)

		// Resume position. Accent, because this is one of the few places a colour
		// carries information rather than decoration.
		if (progress != null && progress > 0f) {
			Box(
				modifier = Modifier
					.align(Alignment.BottomStart)
					.fillMaxWidth()
					.height(2.dp)
					.background(Color.White.copy(alpha = 0.24f))
			) {
				Box(
					modifier = Modifier
						.fillMaxWidth(progress.coerceIn(0f, 1f))
						.height(2.dp)
						.background(Colors.Accent)
				)
			}
		}
	}
}
