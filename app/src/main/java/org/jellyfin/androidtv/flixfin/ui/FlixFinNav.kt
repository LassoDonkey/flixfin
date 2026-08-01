package org.jellyfin.androidtv.flixfin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Dimens
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Motion
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type

/**
 * The two navigation controls, modelled on the current Prime Video TV app.
 *
 * Sections run horizontally across the top; the left rail keeps three unlabelled
 * icons. That split is the point:
 *
 *  - **Sections are peers.** A horizontal strip says "these are alternatives to
 *    each other". A vertical list that expands over the content says "this is a
 *    menu you opened", and hides the content while you use it.
 *  - **"Easier to sway than to headbang"** is Disney Streaming's own phrasing:
 *    tracking focus left/right costs less than up/down, so the movement made most
 *    often should be horizontal.
 *
 * The rail is three items on purpose. A rail you can traverse without looking is
 * a rail you use; at seven you have to read it, and reading a vertical list of
 * labels from a sofa is exactly what the top strip exists to avoid.
 */
enum class RailItem(val label: String) {
	Home("Home"),
	Search("Search"),
	Settings("Settings"),
}

enum class TopItem(val label: String) {
	Films("Films"),
	Shows("TV shows"),
	MyList("My list"),
}

/**
 * The top strip.
 *
 * Sits inside the vertical overscan margin rather than flush to the panel edge:
 * a set with overscan enabled crops that band, and the nav bar is the one piece
 * of chrome that must never be half-visible, because it is how you get anywhere.
 */
@Composable
fun FlixFinTopNav(
	focusedIndex: Int,
	scrolled: Boolean,
	modifier: Modifier = Modifier,
) {
	Box(modifier = modifier.fillMaxWidth()) {
		/*
		 * A backing that fades in once the page has moved.
		 *
		 * The bar stays put while content scrolls under it, so past the hero it
		 * would otherwise sit directly on a row of cards with card titles reading
		 * through the nav labels. Drawn from the very top of the panel, because the
		 * strip above the bar is inside the overscan margin and leaving it clear
		 * would let a card show through above the nav.
		 */
		if (scrolled) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(Dimens.SafeY + Dimens.TopNavHeight + 24.dp)
					.background(
						Brush.verticalGradient(
							0f to Colors.Background,
							0.55f to Colors.Background,
							1f to Color.Transparent,
						)
					)
			)
		}

		Row(
			modifier = Modifier
				.padding(start = Dimens.ContentX, top = Dimens.SafeY, end = Dimens.SafeX)
				.height(Dimens.TopNavHeight),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			/*
			 * The wordmark, and the whole brand budget for the screen.
			 *
			 * Two-tone to match the logo. Deliberately NOT the accent gold: gold is
			 * the interaction colour, and spending it on something permanently on
			 * screen leaves nothing to signal focus with. Netflix's brand colour
			 * covers well under 1% of a full home screen.
			 */
			Row(verticalAlignment = Alignment.CenterVertically) {
				FlixFinText("Flix", size = 14.sp, weight = Type.Bold, color = Colors.BrandRed)
				FlixFinText("Fin", size = 14.sp, weight = Type.Bold, color = Colors.BrandSilver)
			}

			Spacer(Modifier.width(18.dp))

			TopItem.entries.forEachIndexed { i, item ->
				NavPill(label = item.label, focused = i == focusedIndex)
			}
		}
	}
}

@Composable
private fun NavPill(label: String, focused: Boolean) {
	val scale by animateFloatAsState(
		targetValue = if (focused) 1.06f else 1f,
		animationSpec = tween(if (focused) Motion.FocusInMs else Motion.FocusOutMs),
		label = "navPill",
	)

	Box(
		modifier = Modifier
			.scale(scale)
			.clip(Dimens.PillRadius)
			// Active state is a FILL, not a hue shift. Focus must never be carried
			// by colour alone, and a shape reads at three metres where a colour
			// change does not.
			.background(if (focused) Color.White else Color.Transparent)
			.padding(horizontal = 12.dp, vertical = 5.dp),
	) {
		FlixFinText(
			text = label,
			size = Type.Nav,
			weight = Type.Semi,
			color = if (focused) Colors.BackgroundDeep else Colors.TextFaint,
			shadow = !focused,
		)
	}
}

/**
 * The left rail. Three icons, no labels, no panel, no expansion.
 *
 * Netflix's rail has no background surface at all — it is icons on the scrimmed
 * artwork, and separation comes entirely from the scrim. Both earlier attempts
 * in the prototype added something and both were wrong: a left-to-right fade read
 * as a smudge, and a flat strip with a hard border read as a toolbar bolted onto
 * the film.
 *
 * Vertically centred rather than starting under the top strip: it is a small
 * cluster of always-available actions, not a list, and centring stops it reading
 * as the top of a menu that has been cut off.
 */
@Composable
fun FlixFinRail(
	focusedIndex: Int,
	activeIndex: Int,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxHeight()
			.width(Dimens.RailWidth),
		verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		RailItem.entries.forEachIndexed { i, item ->
			val focused = i == focusedIndex
			val scale by animateFloatAsState(
				targetValue = if (focused) Motion.FocusScale else 1f,
				animationSpec = tween(if (focused) Motion.FocusInMs else Motion.FocusOutMs),
				label = "railIcon",
			)

			val tint = when {
				focused -> Colors.BackgroundDeep
				i == activeIndex -> Colors.Text
				else -> Colors.TextFaint
			}

			Box(
				modifier = Modifier
					.size(30.dp)
					.scale(scale)
					.clip(CircleShape)
					.background(if (focused) Color.White else Color.Transparent),
				contentAlignment = Alignment.Center,
			) {
				RailIcon(item = item, tint = tint)
			}
		}
	}
}

/**
 * The rail glyphs, drawn rather than imported.
 *
 * This app does not depend on compose-material, so `Icons.Filled.*` is not
 * available — and pulling in the whole Material icon set for three shapes would
 * be a lot of method count for a build already close to the multidex line.
 *
 * Drawing them also lets the stroke be deliberately heavy. docs/tv-constraints.md
 * section 9: TV gamma eats thin strokes, and these are read from three metres, so
 * a 2dp stroke on a 16dp glyph is the floor rather than a style choice.
 */
@Composable
private fun RailIcon(item: RailItem, tint: Color) {
	Canvas(modifier = Modifier.size(16.dp)) {
		val w = size.width
		val h = size.height
		val stroke = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)

		when (item) {
			RailItem.Home -> {
				// Roof as two strokes from the apex, then the body as a box. Simpler
				// than a single closed path and it survives being scaled down.
				val path = Path().apply {
					moveTo(w * 0.10f, h * 0.45f)
					lineTo(w * 0.50f, h * 0.12f)
					lineTo(w * 0.90f, h * 0.45f)
				}
				drawPath(path, color = tint, style = stroke)
				val body = Path().apply {
					moveTo(w * 0.21f, h * 0.42f)
					lineTo(w * 0.21f, h * 0.88f)
					lineTo(w * 0.79f, h * 0.88f)
					lineTo(w * 0.79f, h * 0.42f)
				}
				drawPath(body, color = tint, style = stroke)
			}

			RailItem.Search -> {
				drawCircle(
					color = tint,
					radius = w * 0.28f,
					center = Offset(w * 0.43f, h * 0.43f),
					style = stroke,
				)
				drawLine(
					color = tint,
					start = Offset(w * 0.64f, h * 0.64f),
					end = Offset(w * 0.88f, h * 0.88f),
					strokeWidth = w * 0.13f,
					cap = StrokeCap.Round,
				)
			}

			RailItem.Settings -> {
				// Sliders, not a cog. A cog's teeth turn to mush below about 20dp,
				// and three horizontal lines with handles stay legible at any size.
				listOf(0.26f, 0.5f, 0.74f).forEachIndexed { row, y ->
					drawLine(
						color = tint,
						start = Offset(w * 0.12f, h * y),
						end = Offset(w * 0.88f, h * y),
						strokeWidth = w * 0.12f,
						cap = StrokeCap.Round,
					)
					// Handles alternate sides so the shape reads as controls rather
					// than as a hamburger menu.
					val x = if (row % 2 == 0) 0.68f else 0.34f
					drawCircle(color = tint, radius = w * 0.11f, center = Offset(w * x, h * y))
				}
			}
		}
	}
}
