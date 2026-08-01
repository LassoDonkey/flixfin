package org.jellyfin.androidtv.flixfin.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FlixFin's design tokens.
 *
 * This is the Kotlin side of `prototype/src/styles.css`. The prototype is where
 * the design was argued about; this is where it ships. Values are carried across
 * deliberately rather than re-derived, so the two stay comparable — if a number
 * here disagrees with the CSS, one of them is a bug.
 *
 * ## Sizes are in dp, and that is not a rename
 *
 * The prototype is authored at a fixed 1920x1080 canvas, so every value in the
 * CSS is a pixel at 1080p. Android TV reports xhdpi at 1080p, meaning
 * **1dp = 2px**, so every CSS pixel is half a dp. A 304px card is 152dp.
 *
 * Getting this backwards would double every dimension, which is the single
 * easiest way to reproduce the "everything is zoomed in" problem the prototype
 * spent a long time fixing.
 *
 * The density assumption is checked at runtime — see [FlixFinHomeScreen], which
 * logs `LocalDensity` once. docs/visual-colour-type.md 3.3 warns explicitly that
 * sp values are density-dependent and that Prime Video's client does not appear
 * to run at 2.0.
 */
object FlixFinTheme {
	object Colors {
		/** Cool blue-black, not neutral charcoal. Most of why this reads unlike Plex. */
		val Background = Color(0xFF060910)
		val BackgroundDeep = Color(0xFF03050A)

		val Text = Color(0xFFFFFFFF)
		val TextDim = Color(0xFFB6C0CF)
		val TextFaint = Color(0xFF78849A)

		/**
		 * The logo's gold — the play button at the centre of the mark.
		 *
		 * Measures 9.47:1 against the background. Was a cyan invented before there
		 * was a logo; see assets/brand/README.md.
		 */
		val Accent = Color(0xFFD4AF37)

		/**
		 * Brand red, lifted for this background.
		 *
		 * The canonical #8B0000 measures **1.99:1** here — below WCAG's 3:1 floor
		 * for large text, on a screen read from three metres. It is correct on the
		 * white artwork and unusable on near-black. Do not "fix" this back.
		 */
		val BrandRed = Color(0xFFD14836)
		val BrandSilver = Color(0xFFDCE3E7)

		/**
		 * White-α over near-black, and the alphas are measured.
		 *
		 * Prime Video's Play button composites to #343841 and its rating chip to
		 * #34373C, both ~0.16 white; Disney+'s TRAILER button is #403F3D at ~0.22
		 * (visual-colour-type.md 2.1). A flat 16–22% white fill is indistinguishable
		 * from frosted glass at three metres, because what reads as "raised" is the
		 * luminance step and not the defocus — which matters because blur is
		 * unavailable below API 31 (tv-constraints.md §1).
		 */
		val Surface = Color(0x14FFFFFF)
		val SurfaceStrong = Color(0x2EFFFFFF)

		/** Panels over artwork tint dark: a white veil over a bright backdrop turns milky. */
		val Scrim = Color(0xE6060910)
	}

	/**
	 * Type scale, in sp, halved from the prototype's 1080p pixels.
	 *
	 * The band is the load-bearing part: **TV UI text lives at 24–30px at 1080p**,
	 * which is 12–15sp, and three independent sources agree — Amazon publishes a
	 * 14sp floor, Apple's tvOS Body is 29pt, and Prime Video's shipped row headers
	 * measure ~25px em (visual-colour-type.md 3.3).
	 */
	object Type {
		val RowHead = 13.sp
		val Nav = 13.sp
		val Button = 13.sp
		val Body = 14.sp
		val Meta = 13.sp
		val Chip = 11.sp
		val ChipKey = 8.sp
		val ScreenTitle = 19.sp

		/** No weight below Medium. TV gamma eats thin strokes (tv-constraints.md §9). */
		val Regular = FontWeight.Medium
		val Semi = FontWeight.SemiBold
		val Bold = FontWeight.Bold
	}

	/**
	 * Geometry, measured off Prime Video's own 1920x1080 frame and halved.
	 *
	 * | Thing | Prime measured (px) | here (dp) |
	 * | card | 383x215 | 152x86 |
	 * | gutter | 30 | 12 |
	 * | left margin | 144 | 72 |
	 */
	object Dimens {
		/** 5% overscan per edge. Google and Amazon both specify it; most sets no
		 *  longer clip, but many ship with overscan on in some picture modes. */
		val SafeX = 48.dp
		val SafeY = 27.dp

		/** Matches the rail width exactly, so the rail occupies the left margin. */
		val ContentX = 72.dp
		val RailWidth = 72.dp

		val CardWide = 152.dp
		val CardWideHeight = 86.dp
		val CardGap = 12.dp

		val EpisodeCard = 180.dp
		val EpisodeCardHeight = 101.dp

		val HeroHeight = 280.dp
		val TopNavHeight = 30.dp

		val TileRadius = RoundedCornerShape(5.dp)
		val PanelRadius = RoundedCornerShape(10.dp)
		val PillRadius = RoundedCornerShape(percent = 50)
	}

	/**
	 * Focus motion, taken from androidx tv-material's own SurfaceScaleTokens.
	 *
	 * Focus in 300ms, out 500ms — the asymmetry is deliberate, so a fast traverse
	 * does not strobe. A held D-pad repeats every 50ms after an initial 400ms, so
	 * these must be interruptible: Compose animations retarget from their current
	 * value, which is what makes that safe.
	 */
	object Motion {
		const val FocusInMs = 300
		const val FocusOutMs = 500

		/**
		 * 1.08 on a 152dp card is +12dp total, ~6dp a side — half the 12dp gutter,
		 * so it grows without colliding (visual-colour-type.md 3.5).
		 */
		const val FocusScale = 1.08f
	}
}
