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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.flixfin.ui.FlixFinCard
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
			.onKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
				when (event.key) {
					Key.DirectionDown -> {
						if (row < home.rows.size) row += 1
						true
					}

					Key.DirectionUp -> {
						if (row > 0) row -= 1
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
						if (row == 0) {
							if (heroIndex > 0) heroIndex -= 1
						} else if (index > 0) {
							index -= 1
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
	}
}

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
