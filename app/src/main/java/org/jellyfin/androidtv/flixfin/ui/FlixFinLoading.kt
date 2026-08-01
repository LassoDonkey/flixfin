package org.jellyfin.androidtv.flixfin.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Dimens
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type

/**
 * What is on screen while the library loads.
 *
 * It used to be nothing at all — a black rectangle — on the reasoning that a
 * placeholder appearing and vanishing within a couple of frames reads as a
 * glitch. That reasoning holds for a *fast* load and falls apart for a slow one:
 * on a cold start over a network the screen sat black for long enough to look
 * broken, which is worse than a placeholder that flickers.
 *
 * So: the mark, the name, and one moving thing. Enough to say "this is FlixFin
 * and it is working" and nothing more.
 *
 * ## One moving element, deliberately
 *
 * Not a shimmer on every placeholder. A screen full of moving highlights that
 * mean nothing is the stock web-template loading effect, it reads as cheap, and
 * it animates dozens of elements at exactly the moment the device is busy
 * fetching. One indeterminate bar says the same thing for the cost of one
 * animated layer.
 */
@Composable
fun FlixFinLoading(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.fillMaxSize()
			/*
			 * A very slight vertical lift rather than flat black.
			 *
			 * Pure black edge to edge is what makes a waiting screen feel switched
			 * off rather than busy. This is barely perceptible as a gradient and
			 * entirely perceptible as "the panel is doing something".
			 */
			.background(
				Brush.verticalGradient(
					0f to Colors.Background,
					0.55f to Colors.BackgroundDeep,
					1f to Colors.BackgroundDeep,
				)
			),
		contentAlignment = Alignment.Center,
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Image(
				painter = painterResource(R.mipmap.app_icon_fg),
				contentDescription = null,
				contentScale = ContentScale.Fit,
				modifier = Modifier.size(74.dp),
			)

			Spacer(Modifier.height(10.dp))

			// Two-tone, matching the logo and the top strip.
			Row {
				FlixFinText("Flix", size = 26.sp, weight = Type.Bold, color = Colors.BrandRed, shadow = false)
				FlixFinText("Fin", size = 26.sp, weight = Type.Bold, color = Colors.BrandSilver, shadow = false)
			}

			Spacer(Modifier.height(22.dp))

			IndeterminateBar()
		}
	}
}

/**
 * A travelling bar, not a spinner.
 *
 * At three metres a small rotating ring is an ambiguous smudge — the eye reads
 * linear travel far more reliably than rotation at that distance. It is also the
 * same shape the boot screen uses, so "the app is working" looks identical
 * wherever it happens.
 */
@Composable
fun IndeterminateBar(
	modifier: Modifier = Modifier,
	width: androidx.compose.ui.unit.Dp = 110.dp,
) {
	val transition = rememberInfiniteTransition(label = "bar")
	val progress by transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			// 1.1s, and it does not reverse. A bar that bounces back reads as
			// indecision; one that always travels the same way reads as progress.
			animation = tween(1100),
			repeatMode = RepeatMode.Restart,
		),
		label = "barProgress",
	)

	Box(
		modifier = modifier
			.width(width)
			.height(3.dp)
			.clip(Dimens.PillRadius)
			.background(Color.White.copy(alpha = 0.12f)),
	) {
		val travel = width * 1.4f
		Box(
			modifier = Modifier
				.offset(x = -width * 0.4f + travel * progress)
				.width(width * 0.34f)
				.height(3.dp)
				.clip(Dimens.PillRadius)
				.background(Colors.Accent)
		)
	}
}

/**
 * The row skeleton, for when the shape is known but the content is not.
 *
 * Flat, and deliberately not animated — see the note on [FlixFinLoading]. Shape
 * and position already say "content is coming and here is where"; motion adds
 * nothing to that message and costs frames.
 */
@Composable
fun FlixFinRowSkeleton(modifier: Modifier = Modifier) {
	Column(modifier = modifier.padding(start = Dimens.ContentX)) {
		Box(
			modifier = Modifier
				.width(90.dp)
				.height(11.dp)
				.clip(Dimens.TileRadius)
				.background(Colors.Surface)
		)
		Spacer(Modifier.height(8.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
			repeat(6) {
				Box(
					modifier = Modifier
						.size(Dimens.CardWide, Dimens.CardWideHeight)
						.clip(Dimens.TileRadius)
						.background(Colors.Surface)
				)
			}
		}
	}
}
