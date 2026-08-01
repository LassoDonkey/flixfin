package org.jellyfin.androidtv.flixfin.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.flixfin.home.ImageKind
import org.jellyfin.androidtv.flixfin.ui.FlixFinCard
import org.jellyfin.androidtv.flixfin.ui.FlixFinText
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Dimens
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Motion
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson

/**
 * What the page is made of, in order, and only what actually loaded.
 *
 * The prototype started with a fixed three rows — actions, cast, more-like-this
 * — which is why opening a series there gave no way to reach an episode: the row
 * existed in neither the model nor the markup. Computing the shape from what came
 * back means a film, a series, and a series with one season are all handled by
 * the same code rather than by special cases.
 */
enum class DetailRow { Back, Actions, Seasons, Episodes, Cast, Related }

private fun rowsFor(detail: FlixFinDetail): List<DetailRow> = buildList {
	add(DetailRow.Back)
	add(DetailRow.Actions)
	// A picker with one option is a control that cannot do anything.
	if (detail.seasons.size > 1) add(DetailRow.Seasons)
	if (detail.episodes.isNotEmpty()) add(DetailRow.Episodes)
	if (detail.cast.isNotEmpty()) add(DetailRow.Cast)
	if (detail.related.isNotEmpty()) add(DetailRow.Related)
}

private fun lengthOf(row: DetailRow, detail: FlixFinDetail): Int = when (row) {
	DetailRow.Back -> 1
	DetailRow.Actions -> if (detail.trailerUrl != null) 2 else 1
	DetailRow.Seasons -> detail.seasons.size
	DetailRow.Episodes -> detail.episodes.size
	DetailRow.Cast -> detail.cast.size
	DetailRow.Related -> detail.related.size
}

/**
 * The detail page.
 *
 * ## No autoplaying trailer, and that is a decision
 *
 * Hulu's patent US 11,960,716 B2 describes its detail-page preview as running on
 * "a streamlined version that is not able to play full length videos" — a
 * second, deliberately cheap player, torn down when you go full screen. Our
 * pipeline is one heavyweight instance (Media3 plus Jellyfin's FFmpeg decoder
 * plus libass) on a 30–40MB graphics budget, so copying the behaviour buys the
 * expensive half of the pattern and none of the cheap half. Drilling through
 * "more like this" would tear a decoder up and down every few seconds.
 *
 * Pressing Trailer still plays one. Being *given* one unasked is what costs.
 */
@Composable
fun FlixFinDetailScreen(
	detail: FlixFinDetail,
	imageUrl: (BaseItemDto, ImageKind) -> String?,
	personImageUrl: (BaseItemPerson) -> String?,
	onPlay: (BaseItemDto) -> Unit,
	onTrailer: (String) -> Unit,
	onOpen: (BaseItemDto) -> Unit,
	onSeason: (Int) -> Unit,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val rows = remember(detail) { rowsFor(detail) }
	var rowIndex by remember(detail.item.id) { mutableIntStateOf(1) }
	var index by remember(rowIndex) { mutableIntStateOf(0) }

	val focusRequester = remember { FocusRequester() }
	val listState = rememberLazyListState()
	LaunchedEffect(detail.item.id) { focusRequester.requestFocus() }

	val row = rows.getOrElse(rowIndex) { DetailRow.Actions }
	val scrolled = rowIndex > rows.indexOf(DetailRow.Actions)

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(Colors.BackgroundDeep)
			.focusRequester(focusRequester)
			.onKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
				when (event.key) {
					Key.DirectionDown -> {
						if (rowIndex < rows.lastIndex) rowIndex += 1
						true
					}

					Key.DirectionUp -> {
						// Up from the actions reaches Back, which is where a
						// "how do I get out of here" reflex goes first.
						if (rowIndex > 0) rowIndex -= 1
						true
					}

					Key.DirectionRight -> {
						if (index < lengthOf(row, detail) - 1) index += 1
						true
					}

					Key.DirectionLeft -> {
						if (index > 0) index -= 1
						true
					}

					Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
						when (row) {
							DetailRow.Back -> onBack()
							DetailRow.Actions -> when (index) {
								0 -> onPlay(detail.item)
								else -> detail.trailerUrl?.let(onTrailer)
							}
							DetailRow.Seasons -> detail.seasons.getOrNull(index)?.let { onSeason(it.number) }
							// Enter on an episode plays THAT episode. Picking one and
							// being given another is the reason the row exists.
							DetailRow.Episodes -> detail.episodes.getOrNull(index)?.let(onPlay)
							DetailRow.Related -> detail.related.getOrNull(index)?.let(onOpen)
							DetailRow.Cast -> Unit
						}
						true
					}

					// Back must fall through, or the page cannot be left with the remote.
					else -> false
				}
			},
	) {
		AsyncImage(
			model = imageUrl(detail.item, ImageKind.Backdrop),
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize(),
		)

		// Directional scrim, same as the home hero. The copy column is sized to
		// stay inside it.
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.horizontalGradient(
						0f to Colors.BackgroundDeep.copy(alpha = 0.92f),
						0.34f to Colors.BackgroundDeep.copy(alpha = 0.6f),
						0.58f to Color.Transparent,
					)
				)
		)

		/*
		 * Hands the backdrop off to a flat background once the page scrolls.
		 *
		 * A detail backdrop is a face at 1920px, which is the worst possible thing
		 * to read small grey type against — and below the fold the page is a cast
		 * strip and rows of cards. The banner is the top of the page; past that it
		 * should be background.
		 */
		val flat by animateFloatAsState(
			targetValue = if (scrolled) 0.9f else 0f,
			animationSpec = tween(420),
			label = "detailFlat",
		)
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Colors.BackgroundDeep.copy(alpha = flat))
		)

		LazyColumn(
			state = listState,
			contentPadding = PaddingValues(
				top = Dimens.SafeY,
				bottom = Dimens.SafeY,
			),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			item {
				DetailHead(
					detail = detail,
					imageUrl = imageUrl,
					focusedRow = row,
					focusedIndex = index,
				)
			}

			if (DetailRow.Seasons in rows) {
				item {
					SeasonStrip(
						detail = detail,
						focused = row == DetailRow.Seasons,
						focusedIndex = if (row == DetailRow.Seasons) index else -1,
					)
				}
			}

			if (DetailRow.Episodes in rows) {
				item {
					EpisodeStrip(
						detail = detail,
						imageUrl = imageUrl,
						focused = row == DetailRow.Episodes,
						focusedIndex = if (row == DetailRow.Episodes) index else -1,
					)
				}
			}

			if (DetailRow.Cast in rows) {
				item {
					CastStrip(
						cast = detail.cast,
						personImageUrl = personImageUrl,
						focused = row == DetailRow.Cast,
						focusedIndex = if (row == DetailRow.Cast) index else -1,
					)
				}
			}

			if (DetailRow.Related in rows) {
				item {
					RelatedStrip(
						related = detail.related,
						imageUrl = imageUrl,
						focused = row == DetailRow.Related,
						focusedIndex = if (row == DetailRow.Related) index else -1,
					)
				}
			}
		}

		LaunchedEffect(rowIndex) {
			listState.animateScrollToItem(if (rowIndex <= 1) 0 else rowIndex - 1)
		}

		/*
		 * The title, pinned once the page scrolls.
		 *
		 * Scroll to the cast and the whole head — logo, metadata, synopsis — is off
		 * the top, leaving a row of faces and a row of cards with nothing saying
		 * what they belong to. On a phone the header and scrollbar carry that; a TV
		 * has neither, and you may have arrived by drilling through three
		 * "more like this" rows.
		 */
		val pinned = imageUrl(detail.item, ImageKind.Logo)
		if (scrolled && pinned != null) {
			AsyncImage(
				model = pinned,
				contentDescription = null,
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(top = Dimens.SafeY, end = Dimens.SafeX)
					.heightIn(max = 26.dp)
					.widthIn(max = 160.dp),
			)
		}
	}
}

@Composable
private fun DetailHead(
	detail: FlixFinDetail,
	imageUrl: (BaseItemDto, ImageKind) -> String?,
	focusedRow: DetailRow,
	focusedIndex: Int,
) {
	Column(modifier = Modifier.padding(start = Dimens.ContentX, end = Dimens.SafeX)) {
		FlixFinPill(
			label = "Home",
			focused = focusedRow == DetailRow.Back,
		)

		Spacer(Modifier.height(10.dp))

		val logo = imageUrl(detail.item, ImageKind.Logo)
		if (logo != null) {
			AsyncImage(
				model = logo,
				contentDescription = detail.item.name,
				contentScale = ContentScale.Fit,
				alignment = Alignment.BottomStart,
				modifier = Modifier.heightIn(max = 60.dp).widthIn(max = 300.dp),
			)
		} else {
			FlixFinText(
				text = detail.item.name.orEmpty(),
				size = 24.sp,
				weight = Type.Bold,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
		}

		Spacer(Modifier.height(8.dp))

		val meta = listOfNotNull(
			detail.item.productionYear?.toString(),
			detail.item.officialRating,
			detail.item.genres?.take(3)?.joinToString(" · "),
		).joinToString("  ·  ")

		if (meta.isNotEmpty()) {
			FlixFinText(meta, size = Type.Meta, color = Colors.TextDim, maxLines = 1)
			Spacer(Modifier.height(8.dp))
		}

		/*
		 * The synopsis widens rather than growing taller past a length threshold.
		 *
		 * A long overview in a narrow column is a tall thin ribbon, and the eye has
		 * to travel a long way back down for each line at three metres. Decided on
		 * character count rather than by measuring the rendered height: measuring
		 * means compose, read layout, recompose, which is a synchronous layout read
		 * per item on hardware that already struggles.
		 */
		val overview = detail.item.overview.orEmpty()
		FlixFinText(
			text = overview,
			size = Type.Body,
			color = Colors.TextDim,
			maxLines = 5,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.widthIn(max = if (overview.length > WIDE_OVERVIEW_CHARS) 470.dp else 320.dp),
		)

		Spacer(Modifier.height(14.dp))

		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			FlixFinPill(
				label = if ((detail.item.userData?.playbackPositionTicks ?: 0) > 0) "Resume" else "Play",
				focused = focusedRow == DetailRow.Actions && focusedIndex == 0,
				primary = true,
			)
			if (detail.trailerUrl != null) {
				FlixFinPill(
					label = "Trailer",
					focused = focusedRow == DetailRow.Actions && focusedIndex == 1,
				)
			}
		}
	}
}

/** A focusable pill. White when focused, because focus must never be colour alone. */
@Composable
private fun FlixFinPill(
	label: String,
	focused: Boolean,
	primary: Boolean = false,
) {
	val scale by animateFloatAsState(
		targetValue = if (focused) Motion.FocusScale else 1f,
		animationSpec = tween(if (focused) Motion.FocusInMs else Motion.FocusOutMs),
		label = "pillScale",
	)

	Box(
		modifier = Modifier
			.scale(scale)
			.clip(Dimens.PillRadius)
			.background(
				when {
					focused -> Color.White
					primary -> Colors.SurfaceStrong
					else -> Colors.Surface
				}
			)
			.padding(horizontal = 18.dp, vertical = 7.dp),
	) {
		FlixFinText(
			text = label,
			size = Type.Button,
			weight = Type.Semi,
			color = if (focused) Colors.BackgroundDeep else Colors.Text,
			shadow = false,
		)
	}
}

@Composable
private fun SectionHead(title: String) = FlixFinText(
	text = title,
	size = Type.RowHead,
	weight = Type.Bold,
	modifier = Modifier.padding(start = Dimens.ContentX, bottom = 6.dp),
)

@Composable
private fun SeasonStrip(detail: FlixFinDetail, focused: Boolean, focusedIndex: Int) {
	Column {
		SectionHead("Seasons")
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			contentPadding = PaddingValues(start = Dimens.ContentX, end = Dimens.SafeX),
		) {
			items(detail.seasons, key = { it.id.toString() }) { season ->
				val i = detail.seasons.indexOf(season)
				FlixFinPill(
					label = if (season.number > 0) "Season ${season.number}" else season.name,
					focused = focused && i == focusedIndex,
				)
			}
		}
	}
}

@Composable
private fun EpisodeStrip(
	detail: FlixFinDetail,
	imageUrl: (BaseItemDto, ImageKind) -> String?,
	focused: Boolean,
	focusedIndex: Int,
) {
	val state = rememberLazyListState()
	LaunchedEffect(focusedIndex) {
		if (focusedIndex >= 0) state.animateScrollToItem((focusedIndex - 1).coerceAtLeast(0))
	}

	Column {
		SectionHead("Episodes")
		LazyRow(
			state = state,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(start = Dimens.ContentX, end = Dimens.SafeX),
		) {
			items(detail.episodes, key = { it.id.toString() }) { episode ->
				val i = detail.episodes.indexOf(episode)
				Column(modifier = Modifier.width(Dimens.EpisodeCard)) {
					FlixFinCard(
						title = "",
						imageUrl = imageUrl(episode, ImageKind.Thumb),
						focused = focused && i == focusedIndex,
						progress = episode.userData?.playedPercentage?.toFloat()?.div(100f),
						modifier = Modifier.size(Dimens.EpisodeCard, Dimens.EpisodeCardHeight),
					)
					Spacer(Modifier.height(6.dp))
					FlixFinText(
						text = "S${episode.parentIndexNumber ?: 1}E${episode.indexNumber ?: (i + 1)}",
						size = Type.ChipKey,
						weight = Type.Bold,
						color = Colors.Accent,
					)
					FlixFinText(
						text = episode.name.orEmpty(),
						size = Type.Chip,
						weight = Type.Semi,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					/*
					 * Episode synopses are the one place running text earns its space:
					 * stills from one season are often the same set and the same faces,
					 * so the artwork cannot tell one card from the next.
					 */
					FlixFinText(
						text = episode.overview.orEmpty(),
						size = Type.ChipKey,
						color = Colors.TextFaint,
						maxLines = 3,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
		}
	}
}

@Composable
private fun CastStrip(
	cast: List<BaseItemPerson>,
	personImageUrl: (BaseItemPerson) -> String?,
	focused: Boolean,
	focusedIndex: Int,
) {
	val state = rememberLazyListState()
	LaunchedEffect(focusedIndex) {
		if (focusedIndex >= 0) state.animateScrollToItem((focusedIndex - 2).coerceAtLeast(0))
	}

	Column {
		SectionHead("Cast")
		LazyRow(
			state = state,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(start = Dimens.ContentX, end = Dimens.SafeX),
		) {
			items(cast, key = { it.id.toString() }) { person ->
				val i = cast.indexOf(person)
				val isFocused = focused && i == focusedIndex
				val scale by animateFloatAsState(
					targetValue = if (isFocused) Motion.FocusScale else 1f,
					animationSpec = tween(if (isFocused) Motion.FocusInMs else Motion.FocusOutMs),
					label = "castScale",
				)

				Column(
					modifier = Modifier.width(66.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					Box(
						modifier = Modifier
							.size(56.dp)
							.scale(scale)
							.clip(CircleShape)
							.background(Colors.Surface)
							.then(
								if (isFocused) Modifier.border(2.dp, Color.White, CircleShape)
								else Modifier
							),
					) {
						AsyncImage(
							model = personImageUrl(person),
							contentDescription = null,
							contentScale = ContentScale.Crop,
							modifier = Modifier.fillMaxSize(),
						)
					}
					Spacer(Modifier.height(5.dp))
					/*
					 * Two lines always reserved, even for a one-line name.
					 *
					 * Without it the role sits directly under whatever height the name
					 * took, so "Jon Bernthal / Frank Castle" and "Jason R. Moore /
					 * Curtis Hoyle" put their roles at different heights and the strip
					 * reads as broken rather than as a row.
					 */
					FlixFinText(
						text = person.name.orEmpty(),
						size = Type.ChipKey,
						weight = Type.Semi,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.heightIn(min = 22.dp),
					)
					FlixFinText(
						text = person.role.orEmpty(),
						size = Type.ChipKey,
						color = Colors.TextFaint,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
		}
	}
}

@Composable
private fun RelatedStrip(
	related: List<BaseItemDto>,
	imageUrl: (BaseItemDto, ImageKind) -> String?,
	focused: Boolean,
	focusedIndex: Int,
) {
	val state = rememberLazyListState()
	LaunchedEffect(focusedIndex) {
		if (focusedIndex >= 0) state.animateScrollToItem((focusedIndex - 1).coerceAtLeast(0))
	}

	Column {
		SectionHead("More like this")
		LazyRow(
			state = state,
			horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
			contentPadding = PaddingValues(start = Dimens.ContentX, end = Dimens.SafeX),
		) {
			items(related, key = { it.id.toString() }) { item ->
				FlixFinCard(
					title = item.name.orEmpty(),
					imageUrl = imageUrl(item, ImageKind.Thumb),
					focused = focused && related.indexOf(item) == focusedIndex,
				)
			}
		}
	}
}

private const val WIDE_OVERVIEW_CHARS = 200
