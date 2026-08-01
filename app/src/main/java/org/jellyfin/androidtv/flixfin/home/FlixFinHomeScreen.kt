package org.jellyfin.androidtv.flixfin.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import org.jellyfin.androidtv.flixfin.ui.FlixFinCard
import org.jellyfin.androidtv.flixfin.ui.FlixFinRail
import org.jellyfin.androidtv.flixfin.ui.FlixFinTopNav
import org.jellyfin.androidtv.flixfin.ui.RailItem
import org.jellyfin.androidtv.flixfin.ui.TopItem
import org.jellyfin.androidtv.flixfin.ui.FlixFinHero
import org.jellyfin.androidtv.flixfin.ui.FlixFinHeroDots
import org.jellyfin.androidtv.flixfin.ui.FlixFinText
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Dimens
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber

/**
 * The home screen.
 *
 * ## Focus is driven by key events, not by Compose's focus system
 *
 * Deliberate, and worth explaining because it looks like the wrong choice.
 *
 * The design needs the hero to track whatever is focused, rows to slide so the
 * focused card sits at a fixed x, and the page to slide so the focused row sits
 * at a fixed y. All three are one piece of state — "where am I" — and deriving
 * that from Compose's focus traversal means reading focus back out of the tree
 * and hoping it agrees with what was rendered.
 *
 * Owning a `(row, index)` cursor makes the whole screen a pure function of it.
 * It is also how the prototype worked, which means the behaviour that was argued
 * over transfers exactly rather than being re-derived.
 *
 * The cost is that this composable must hold focus itself and consume D-pad
 * keys; that is what the [FocusRequester] and `onKeyEvent` below are for.
 */
@Composable
fun FlixFinHomeScreen(
	home: FlixFinHome,
	onOpen: (BaseItemDto) -> Unit,
	imageUrl: (BaseItemDto, ImageKind) -> String?,
	onRail: (RailItem) -> Unit,
	onTop: (TopItem) -> Unit,
	modifier: Modifier = Modifier,
) {
	/*
	 * Row 0 is the hero; rows 1..n are the content rows.
	 *
	 * The hero being row 0 rather than a separate mode is what makes Up from the
	 * first row land somewhere sensible without a special case.
	 */
	var row by remember { mutableIntStateOf(0) }
	var index by remember(row) { mutableIntStateOf(0) }
	var heroIndex by remember { mutableIntStateOf(0) }

	/*
	 * Three zones, matching the prototype: the content, the top section strip, and
	 * the left rail. Held as one enum rather than as booleans so "which zone" can
	 * only ever have one answer.
	 */
	var zone by remember { mutableStateOf(Zone.Content) }
	var railIndex by remember { mutableIntStateOf(0) }
	var topIndex by remember { mutableIntStateOf(0) }

	val focusRequester = remember { FocusRequester() }
	val listState = rememberLazyListState()

	LaunchedEffect(Unit) {
		focusRequester.requestFocus()

		/*
		 * Log the density once.
		 *
		 * Every dimension in FlixFinTheme assumes 1dp = 2px, which is what a 1080p
		 * Android TV reports as xhdpi. docs/visual-colour-type.md 3.3 warns
		 * explicitly that this is not safe to assume — Prime Video's own measured
		 * type suggests its client is not at 2.0 — and getting it wrong scales the
		 * entire interface by a constant, which is exactly the "everything is
		 * zoomed in" failure the design spent a long time removing.
		 *
		 * One line in logcat beats guessing from a photograph of a television.
		 */
		Timber.i("FlixFin: density check — this UI assumes 2.0 (xhdpi at 1080p)")
	}

	LaunchedEffect(row) {
		// Keep the focused row pinned rather than letting it drift to the bottom.
		if (row > 0) listState.animateScrollToItem((row - 1).coerceAtLeast(0))
	}

	/** What the hero shows: the focused card, or the gallery when on the hero. */
	val heroItem = when {
		row == 0 -> home.featured.getOrNull(heroIndex)
		else -> home.rows.getOrNull(row - 1)?.items?.getOrNull(index)
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(Colors.Background)
			.focusRequester(focusRequester)
			/*
			 * `.focusable()` is not optional, and leaving it out is why the first
			 * build on hardware was an unresponsive picture.
			 *
			 * `onKeyEvent` only fires on a FOCUSED element. Without focusable() this
			 * Box can never take focus, so `focusRequester.requestFocus()` silently
			 * does nothing and not one key ever reaches the handler below. There is
			 * no error and no warning — the screen renders perfectly and ignores the
			 * remote.
			 *
			 * Order matters too: focusRequester must come BEFORE focusable, or the
			 * requester has nothing to attach to.
			 */
			.focusable()
			.onKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

				// --- left rail ---
				if (zone == Zone.Rail) {
					return@onKeyEvent when (event.key) {
						Key.DirectionDown -> {
							railIndex = (railIndex + 1).coerceAtMost(RailItem.entries.lastIndex); true
						}
						Key.DirectionUp -> { railIndex = (railIndex - 1).coerceAtLeast(0); true }
						Key.DirectionRight -> { zone = Zone.Content; true }
						Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
							onRail(RailItem.entries[railIndex]); zone = Zone.Content; true
						}
						else -> false
					}
				}

				// --- top section strip ---
				if (zone == Zone.Top) {
					return@onKeyEvent when (event.key) {
						Key.DirectionRight -> {
							topIndex = (topIndex + 1).coerceAtMost(TopItem.entries.lastIndex); true
						}
						Key.DirectionLeft -> {
							// Left off the first item drops into the rail rather than
							// dead-ending, so the two nav controls reach each other.
							if (topIndex == 0) zone = Zone.Rail else topIndex -= 1
							true
						}
						Key.DirectionDown -> { zone = Zone.Content; true }
						Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
							onTop(TopItem.entries[topIndex]); true
						}
						else -> false
					}
				}

				when (event.key) {
					Key.DirectionDown -> {
						if (row < home.rows.size) row += 1
						true
					}

					Key.DirectionUp -> {
						// Up from the hero goes to the section strip above it.
						if (row > 0) row -= 1 else zone = Zone.Top
						true
					}

					Key.DirectionRight -> {
						if (row == 0) {
							if (heroIndex < home.featured.lastIndex) heroIndex += 1
						} else {
							val length = home.rows.getOrNull(row - 1)?.items?.size ?: 0
							if (index < length - 1) index += 1
						}
						true
					}

					Key.DirectionLeft -> {
						// Left at the start of a row opens the rail, the way Prime does
						// it, so no dedicated menu button is needed on the remote.
						when {
							row == 0 && heroIndex > 0 -> heroIndex -= 1
							row == 0 -> zone = Zone.Rail
							index > 0 -> index -= 1
							else -> zone = Zone.Rail
						}
						true
					}

					Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
						heroItem?.let(onOpen)
						true
					}

					// Everything else — Back especially — must fall through to the
					// activity, or there is no way out of the screen.
					else -> false
				}
			},
	) {
		Column(modifier = Modifier.fillMaxSize()) {
			/*
			 * No spacer above the hero. There used to be one, reserving the height of
			 * the top strip, and it put a hard-edged black band across the top of
			 * every backdrop — the artwork started BELOW the nav instead of running
			 * underneath it.
			 *
			 * The nav is an overlay, not a row in a column. Prime's hero runs to the
			 * very top of the panel with the strip floating on it; the strip earns
			 * its legibility from a gradient, not from a reserved block of
			 * background. The hero's own content is bottom-aligned, so nothing
			 * collides with the bar.
			 */
			FlixFinHero(
				title = heroItem?.name.orEmpty(),
				logoUrl = heroItem?.let { imageUrl(it, ImageKind.Logo) },
				backdropUrl = heroItem?.let { imageUrl(it, ImageKind.Backdrop) },
				meta = heroItem?.let(::metaLine).orEmpty(),
				genres = heroItem?.genres?.take(3)?.joinToString(" · ").orEmpty(),
				continueWatching = heroItem?.userData?.playedPercentage != null,
			)

			if (row == 0 && home.featured.size > 1) {
				FlixFinHeroDots(
					count = home.featured.size,
					index = heroIndex,
					modifier = Modifier.padding(start = Dimens.ContentX, bottom = 10.dp),
				)
			}

			LazyColumn(
				state = listState,
				contentPadding = PaddingValues(bottom = Dimens.SafeY),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				items(home.rows, key = { it.id }) { contentRow ->
					val rowIndex = home.rows.indexOf(contentRow) + 1
					FlixFinRowStrip(
						row = contentRow,
						focused = row == rowIndex,
						focusedIndex = if (row == rowIndex) index else -1,
						imageUrl = imageUrl,
					)
				}
			}
		}

		// Chrome sits above the content and outside the scroll, so it stays put
		// while the page moves underneath.
		FlixFinTopNav(
			focusedIndex = if (zone == Zone.Top) topIndex else -1,
			scrolled = row > 1,
			modifier = Modifier.align(Alignment.TopStart),
		)

		FlixFinRail(
			focusedIndex = if (zone == Zone.Rail) railIndex else -1,
			activeIndex = 0,
			modifier = Modifier.align(Alignment.CenterStart),
		)
	}
}

/** Which of the three controls currently owns the remote. */
private enum class Zone { Content, Rail, Top }

@Composable
private fun FlixFinRowStrip(
	row: FlixFinRow,
	focused: Boolean,
	focusedIndex: Int,
	imageUrl: (BaseItemDto, ImageKind) -> String?,
) {
	val state = rememberLazyListState()

	/*
	 * Keep the first two cards still, then slide so the focused card sits at the
	 * left edge. Sliding from the very first press feels twitchy and costs the
	 * "I am at the start of this row" cue.
	 */
	LaunchedEffect(focusedIndex) {
		if (focusedIndex >= 0) state.animateScrollToItem((focusedIndex - 1).coerceAtLeast(0))
	}

	Column {
		FlixFinText(
			text = row.title,
			size = Type.RowHead,
			weight = Type.Bold,
			modifier = Modifier.padding(start = Dimens.ContentX, bottom = 6.dp),
		)

		LazyRow(
			state = state,
			horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
			contentPadding = PaddingValues(start = Dimens.ContentX, end = Dimens.SafeX),
			// Vertical padding with a matching negative offset would be needed to
			// avoid clipping the 1.08 focus growth; LazyRow does not clip its
			// children vertically, so the row simply needs the room below it.
			modifier = Modifier.fillMaxWidth(),
		) {
			items(row.items, key = { it.id.toString() }) { item ->
				FlixFinCard(
					title = item.name.orEmpty(),
					imageUrl = imageUrl(item, ImageKind.Thumb),
					focused = focused && row.items.indexOf(item) == focusedIndex,
					progress = item.userData?.playedPercentage?.toFloat()?.div(100f),
				)
			}
		}

		// Room for the focused card to grow into without overlapping the next row.
		// Apple's grid specifies a 100pt minimum vertical gap for exactly this
		// reason (visual-colour-type.md 3.5).
		Spacer(Modifier.height(4.dp))
	}
}

/** One short line: year, runtime, certificate. Long enough to place the film, no longer. */
private fun metaLine(item: BaseItemDto): String = listOfNotNull(
	item.productionYear?.toString(),
	item.runTimeTicks?.let { ticks ->
		val minutes = ticks / TICKS_PER_MINUTE
		if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
	},
	item.officialRating,
).joinToString("  ·  ")

private const val TICKS_PER_MINUTE = 600_000_000L

/**
 * Which artwork to ask the server for.
 *
 * Jellyfin stores several distinct image types and they are NOT interchangeable
 * — a portrait poster stretched into a 16:9 banner is the clearest tell of an
 * amateur client. Requesting a type an item lacks returns 404, so the caller
 * checks the item's tags first rather than flashing a broken image.
 */
enum class ImageKind { Primary, Backdrop, Thumb, Logo }
