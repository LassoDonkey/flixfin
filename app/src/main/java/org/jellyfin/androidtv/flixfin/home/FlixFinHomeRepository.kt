package org.jellyfin.androidtv.flixfin.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber

/**
 * One row of the home screen.
 *
 * @param id stable across reloads, so focus can be restored to the same row.
 */
data class FlixFinRow(
	val id: String,
	val title: String,
	val items: List<BaseItemDto>,
)

data class FlixFinHome(
	val rows: List<FlixFinRow>,
	/** The hero gallery, in order. First entry is what you land on. */
	val featured: List<BaseItemDto>,
)

/**
 * Builds the home screen.
 *
 * This is the Kotlin port of `prototype/src/jellyfin.ts` `loadHome()`, and the
 * shape it produces was settled there against the real library rather than
 * guessed. The ordering is the point: personal rows first, then discovery, then
 * categories — tv-constraints.md §11a found users fixate on the top-left and
 * judge a row against what is already on screen, so the top two rows carry most
 * of the value and near-duplicate rows carry almost none.
 */
class FlixFinHomeRepository(
	private val api: ApiClient,
) {
	/**
	 * Genre categories, with the two TMDB vocabularies merged.
	 *
	 * TMDB uses different genre lists for films and for television, and Jellyfin
	 * stores both verbatim. So a library ends up with "Action" AND "Action &
	 * Adventure", "Science Fiction" AND "Sci-Fi & Fantasy" — the same category
	 * twice, with the films in one row and the shows in the other. Merging them is
	 * what makes a category row actually mean the category.
	 *
	 * Order is by how much of a typical library each covers, not alphabetical: a
	 * row with three things in it is not worth the vertical space.
	 */
	private val categories = listOf(
		"Action & adventure" to listOf("Action", "Action & Adventure", "Adventure"),
		"Sci-fi & fantasy" to listOf("Science Fiction", "Sci-Fi & Fantasy", "Fantasy"),
		"Comedy" to listOf("Comedy"),
		"Drama" to listOf("Drama"),
		"Thrillers" to listOf("Thriller"),
		"Crime" to listOf("Crime"),
		"Mystery" to listOf("Mystery"),
		"Horror" to listOf("Horror"),
		"Family & animation" to listOf("Family", "Animation"),
		"Romance" to listOf("Romance"),
		"History & war" to listOf("History", "War", "War & Politics"),
	)

	suspend fun load(): FlixFinHome = withContext(Dispatchers.IO) {
		coroutineScope {
			val resume = async { resumeItems() }
			val nextUp = async { nextUpItems() }
			val recent = async { recentItems() }
			val topRated = async { topRatedItems() }
			val categoryRows = async { categoryRows() }

			/*
			 * "Carry on watching" and "Next up" are ONE row.
			 *
			 * Split, they routinely produce a row containing a single card with most
			 * of the screen empty beside it — measured on the real account: 0 resume
			 * items and 1 next-up episode. A row is read as a row before any card in
			 * it is, and a row with one thing in it reads as something that failed to
			 * load.
			 *
			 * They are also one answer to one question. No streaming service splits
			 * them. Resume leads, because a part-watched film is more urgent than an
			 * unstarted episode.
			 */
			val continueWatching = (resume.await() + nextUp.await()).distinctBy { it.id }

			val rows = buildList {
				if (continueWatching.size >= MIN_PERSONAL_ROW) {
					add(FlixFinRow("continue", "Continue watching", continueWatching))
				}
				recent.await().takeIf { it.isNotEmpty() }?.let { add(FlixFinRow("recent", "Just added", it)) }
				topRated.await().takeIf { it.isNotEmpty() }?.let { add(FlixFinRow("top", "Highest rated", it)) }
				addAll(categoryRows.await())
			}

			/*
			 * Too few to be a row means they LEAD the featured gallery instead.
			 *
			 * The point was never to hide a sparse row: the thing you are halfway
			 * through is the most likely reason you opened the app, so it belongs at
			 * full width with artwork and a Play button rather than as one card in a
			 * strip. It gets more prominence this way, not less.
			 */
			val pool = topRated.await().ifEmpty { recent.await() }
			val featured = when {
				continueWatching.isEmpty() -> pool
				continueWatching.size >= MIN_PERSONAL_ROW -> pool
				else -> continueWatching + pool.filterNot { p -> continueWatching.any { it.id == p.id } }
			}

			FlixFinHome(rows, featured.take(HERO_COUNT))
		}
	}

	private suspend fun resumeItems(): List<BaseItemDto> = runCatching {
		val response by api.itemsApi.getResumeItems(
			limit = ROW_LIMIT,
			fields = ItemRepository.browseFields,
			mediaTypes = listOf(MediaType.VIDEO),
			excludeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
			enableTotalRecordCount = false,
		)
		response.items
	}.onFailure { Timber.w(it, "FlixFin: resume row failed") }.getOrDefault(emptyList())

	/**
	 * Next up, with unstarted series filtered out **client-side**.
	 *
	 * The HTTP API has a `disableFirstEpisode` flag for exactly this, and the
	 * prototype used it. **The SDK does not expose it** — checked against
	 * jellyfin-model 1.8.12: `GetNextUpRequest` has userId, startIndex, limit,
	 * fields, seriesId, parentId, enableImages, imageTypeLimit, enableImageTypes,
	 * enableUserData, nextUpDateCutoff, enableTotalRecordCount, enableResumable
	 * and enableRewatching, and nothing else.
	 *
	 * Without it the row includes the first episode of every series you have never
	 * touched, so "next up" fills with shows you have not started — which is not
	 * what those words mean on any streaming service.
	 *
	 * The filter below is the equivalent, and it is sound rather than approximate:
	 * if you have watched nothing of a series, next-up is S1E1; if you have
	 * watched S1E1, next-up is S1E2. So an unwatched S1E1 in this response means
	 * an unstarted series, near enough always. A partly-watched S1E1 would be in
	 * Resume rather than here, and `enableResumable = false` guarantees it.
	 */
	private suspend fun nextUpItems(): List<BaseItemDto> = runCatching {
		val response by api.tvShowsApi.getNextUp(
			limit = ROW_LIMIT,
			fields = ItemRepository.browseFields,
			enableResumable = false,
			enableTotalRecordCount = false,
		)
		response.items.filterNot { it.isUnstartedFirstEpisode() }
	}.onFailure { Timber.w(it, "FlixFin: next-up row failed") }.getOrDefault(emptyList())

	private fun BaseItemDto.isUnstartedFirstEpisode(): Boolean =
		indexNumber == 1 &&
			parentIndexNumber == 1 &&
			(userData?.playCount ?: 0) == 0

	private suspend fun recentItems(): List<BaseItemDto> = items(
		sortBy = ItemSortBy.DATE_CREATED,
		limit = ROW_LIMIT_WIDE,
	)

	private suspend fun topRatedItems(): List<BaseItemDto> = items(
		sortBy = ItemSortBy.COMMUNITY_RATING,
		limit = ROW_LIMIT_WIDE,
	)

	private suspend fun categoryRows(): List<FlixFinRow> = coroutineScope {
		categories
			.map { (title, genres) ->
				async {
					title to items(
						sortBy = ItemSortBy.COMMUNITY_RATING,
						limit = ROW_LIMIT_WIDE,
						genres = genres,
					)
				}
			}
			.map { it.await() }
			// A category row is only worth the vertical space if it can fill the
			// screen width. Below this it reads as a gap rather than a category —
			// four cards and then emptiness looks like something failed to load.
			.filter { (_, items) -> items.size >= MIN_CATEGORY_ROW }
			.map { (title, items) -> FlixFinRow("genre-$title", title, items) }
	}

	private suspend fun items(
		sortBy: ItemSortBy,
		limit: Int,
		genres: List<String>? = null,
	): List<BaseItemDto> = runCatching {
		val response by api.itemsApi.getItems(
			includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			recursive = true,
			sortBy = listOf(sortBy),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = limit,
			fields = ItemRepository.browseFields,
			genres = genres,
			enableTotalRecordCount = false,
		)
		response.items
	}.onFailure { Timber.w(it, "FlixFin: item query failed (sortBy=$sortBy)") }
		.getOrDefault(emptyList())

	private companion object {
		const val ROW_LIMIT = 12
		const val ROW_LIMIT_WIDE = 24
		const val HERO_COUNT = 8

		/** Below this it is not a row — see the note where it is used. */
		const val MIN_PERSONAL_ROW = 3
		const val MIN_CATEGORY_ROW = 5
	}
}
