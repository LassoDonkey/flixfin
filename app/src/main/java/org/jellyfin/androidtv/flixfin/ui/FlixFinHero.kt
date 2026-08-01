package org.jellyfin.androidtv.flixfin.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Dimens
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type

/**
 * The immersive hero.
 *
 * **It shows whatever is currently focused**, not a fixed featured title. Move
 * along a row and the backdrop, logo and metadata all follow. Netflix, Prime and
 * Max all browse this way, and it is also the only affordable way to do previews
 * on this hardware: HBO Max plays into the hero region rather than inside the
 * tile, which is one video surface and one decoder. A surface per tile does not
 * fit in a Fire TV's 30–40MB graphics budget (tv-constraints.md §11).
 *
 * ## No synopsis
 *
 * The current Prime Video home screen carries none at all — brand lockup, title
 * artwork, one badge line, one button, dots, and that is the whole hero. Netflix
 * runs four lines but it is the only running text on their entire screen, about
 * 25 typeset words in a full frame. Ours would be competing with row headers and
 * a metadata line, which is how a home screen turns into a catalogue entry. The
 * full text is one press away on the detail page.
 *
 * ## The scrim, not a panel
 *
 * All three of Netflix, Prime and Disney+ scrim the artwork directionally; none
 * of them box the copy. Netflix's covers roughly the **left 35–40%** of the hero
 * and the chrome sits straight on it (visual-colour-type.md 1.1, 2.1). The copy
 * column is sized to fit inside that band — if it runs wider, the ends of lines
 * land on raw artwork.
 */
@Composable
fun FlixFinHero(
	title: String,
	logoUrl: String?,
	backdropUrl: String?,
	meta: String,
	genres: String,
	continueWatching: Boolean,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(Dimens.HeroHeight),
	) {
		// Cross-fades as focus moves along a row. 260ms is brisk enough to keep up
		// with a held D-pad without strobing.
		Crossfade(
			targetState = backdropUrl,
			animationSpec = tween(260),
			label = "heroBackdrop",
		) { url ->
			AsyncImage(
				model = url,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		}

		// Vertical scrim: hands the artwork off into the rows below.
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						0f to Color.Transparent,
						0.46f to Colors.Background.copy(alpha = 0.2f),
						0.72f to Colors.Background.copy(alpha = 0.66f),
						1f to Colors.Background,
					)
				)
		)

		/*
		 * Side scrim: strong across the copy column, then off a cliff.
		 *
		 * Netflix's measured horizontal walk is darker than instinct suggests —
		 * #232524 at the far left, through near-black, and only then into bright
		 * artwork. A smooth ramp from the edge spends its opacity where there is no
		 * text and runs out exactly where the text still needs it, so this holds
		 * high alpha flat across the column instead.
		 */
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.horizontalGradient(
						0f to Colors.BackgroundDeep.copy(alpha = 0.94f),
						0.22f to Colors.BackgroundDeep.copy(alpha = 0.9f),
						0.34f to Colors.BackgroundDeep.copy(alpha = 0.62f),
						0.48f to Color.Transparent,
					)
				)
		)

		Column(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(start = Dimens.ContentX, bottom = 22.dp, end = Dimens.SafeX)
				// Must fit inside the side scrim above, or the last words of each
				// line sit on raw artwork.
				.widthIn(max = 320.dp),
			verticalArrangement = Arrangement.Bottom,
		) {
			/*
			 * Says why this item is here.
			 *
			 * Continue-watching items lead the gallery when there are too few of them
			 * to justify a row of their own; without a label that is indistinguishable
			 * from a random featured pick — the most useful item on the screen looking
			 * like the least.
			 */
			if (continueWatching) {
				FlixFinText(
					text = "CONTINUE WATCHING",
					size = Type.ChipKey,
					weight = Type.Bold,
					color = Colors.Accent,
					letterSpacing = 1.4.sp,
				)
				Spacer(Modifier.height(7.dp))
			}

			/*
			 * The title's own artwork ("clearlogo") when the server has one.
			 *
			 * This is the single biggest difference between a streaming app and a
			 * database front-end: Netflix, Prime and Disney+ all show designed title
			 * treatments, not the film's name set in the UI font — two of two apps in
			 * the sample, and it is called out as the most transferable typographic
			 * decision on the screen (visual-colour-type.md 1.1).
			 *
			 * Falls back to typeset text, because a missing logo must degrade to
			 * something readable rather than to a hole.
			 */
			if (logoUrl != null) {
				AsyncImage(
					model = logoUrl,
					contentDescription = title,
					contentScale = ContentScale.Fit,
					alignment = Alignment.BottomStart,
					modifier = Modifier
						.heightIn(max = 56.dp)
						.widthIn(max = 280.dp),
				)
			} else {
				FlixFinText(
					text = title,
					size = 26.sp,
					weight = Type.Bold,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}

			Spacer(Modifier.height(10.dp))

			// One short metadata line, which is what Prime and Netflix both carry.
			if (meta.isNotEmpty()) {
				FlixFinText(
					text = meta,
					size = Type.Meta,
					color = Colors.TextDim,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			if (genres.isNotEmpty()) {
				Spacer(Modifier.height(4.dp))
				FlixFinText(
					text = genres,
					size = Type.Meta,
					color = Colors.TextDim,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

/**
 * The gallery position dots.
 *
 * Separate from the hero because they belong to the *gallery*, not to the item
 * being shown, and putting them inside would mean the hero needed to know how
 * many siblings it has.
 */
@Composable
fun FlixFinHeroDots(
	count: Int,
	index: Int,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		repeat(count) { i ->
			Box(
				modifier = Modifier
					.height(6.dp)
					// The active dot is a wider pill rather than a brighter dot:
					// shape reads at three metres where a luminance change does not.
					.widthIn(min = if (i == index) 18.dp else 6.dp, max = if (i == index) 18.dp else 6.dp)
					.background(
						color = if (i == index) Colors.Accent else Color.White.copy(alpha = 0.26f),
						shape = Dimens.PillRadius,
					)
			)
		}
	}
}
