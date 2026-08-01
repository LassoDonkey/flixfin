package org.jellyfin.androidtv.flixfin.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

data class FlixFinSeason(
	val id: UUID,
	/** Jellyfin's season number. Specials are 0. */
	val number: Int,
	val name: String,
)

data class FlixFinDetail(
	val item: BaseItemDto,
	val cast: List<BaseItemPerson>,
	val seasons: List<FlixFinSeason>,
	val episodes: List<BaseItemDto>,
	val related: List<BaseItemDto>,
	/** Null when the server holds no trailer, which disables the button rather than hiding it. */
	val trailerUrl: String?,
)

/**
 * Everything the detail page shows.
 *
 * Kotlin port of the prototype's DetailOverlay data layer. The shape was settled
 * there against the real library, including the parts that only show up with real
 * data — see [similarTo] for the one that matters most.
 */
class FlixFinDetailRepository(
	private val api: ApiClient,
) {
	suspend fun load(itemId: UUID, season: Int? = null): FlixFinDetail? =
		withContext(Dispatchers.IO) {
			val item = runCatching {
				val response by api.userLibraryApi.getItem(itemId = itemId)
				response
			}.onFailure { Timber.e(it, "FlixFin: detail item failed") }.getOrNull()
				?: return@withContext null

			coroutineScope {
				val seasonsJob = async {
					if (item.type == BaseItemKind.SERIES) seasonsFor(itemId) else emptyList()
				}
				val relatedJob = async { similarTo(item) }

				val seasons = seasonsJob.await()
				val chosen = season ?: seasons.firstOrNull()?.number
				val episodes = if (chosen != null) episodesFor(itemId, chosen) else emptyList()

				FlixFinDetail(
					item = item,
					/*
					 * Actors only. Jellyfin's `people` also carries directors, writers,
					 * producers and composers, and mixing them into a row of faces is
					 * confusing — crew belongs in a metadata line, not a cast strip.
					 */
					cast = item.people.orEmpty()
						.filter { it.type == PersonKind.ACTOR && !it.name.isNullOrBlank() }
						.take(CAST_LIMIT),
					seasons = seasons,
					episodes = episodes,
					related = relatedJob.await(),
					trailerUrl = item.remoteTrailers?.firstOrNull()?.url,
				)
			}
		}

	private suspend fun seasonsFor(seriesId: UUID): List<FlixFinSeason> = runCatching {
		val response by api.tvShowsApi.getSeasons(seriesId = seriesId)
		response.items.map {
			FlixFinSeason(
				id = it.id,
				number = it.indexNumber ?: 0,
				name = it.name.orEmpty(),
			)
		}
	}.onFailure { Timber.w(it, "FlixFin: seasons failed") }.getOrDefault(emptyList())

	/**
	 * Episodes of one season, asked for by season NUMBER.
	 *
	 * The SDK accepts either a number or a season id, and the number is what the
	 * picker already has. Going via the id means holding two pieces of state that
	 * have to agree, which is a bug waiting to happen the first time a series has
	 * a specials season numbered 0.
	 */
	private suspend fun episodesFor(seriesId: UUID, season: Int): List<BaseItemDto> = runCatching {
		val response by api.tvShowsApi.getEpisodes(
			seriesId = seriesId,
			season = season,
			fields = ItemRepository.browseFields,
		)
		response.items.take(EPISODE_LIMIT)
	}.onFailure { Timber.w(it, "FlixFin: episodes failed") }.getOrDefault(emptyList())

	/**
	 * Similar items, with a fallback that matters on a small library.
	 *
	 * Jellyfin scores similarity on shared metadata — genre, people, studio, tags.
	 * On a large library that is plenty. On a personal one it returns **zero** for
	 * a good proportion of titles: measured against the real server, the Avengers
	 * films get 14 each while other titles get none at all. There is simply not
	 * enough overlap for the scorer to work with.
	 *
	 * So an empty result is normal here rather than an error. The prototype
	 * originally filled this row from invented data, which meant a real film showed
	 * five fictional titles under a real cast list — a row that is wrong is worse
	 * than a row that is absent. The fallback is the item's first genre, highest
	 * rated, excluding itself: weaker than real similarity, but true.
	 */
	private suspend fun similarTo(item: BaseItemDto): List<BaseItemDto> {
		val similar = runCatching {
			val response by api.libraryApi.getSimilarItems(
				itemId = item.id,
				limit = RELATED_LIMIT,
				fields = ItemRepository.browseFields,
			)
			response.items
		}.onFailure { Timber.w(it, "FlixFin: similar failed") }.getOrDefault(emptyList())

		if (similar.isNotEmpty()) return similar

		val genre = item.genres?.firstOrNull() ?: return emptyList()
		return runCatching {
			val response by api.itemsApi.getItems(
				includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				recursive = true,
				genres = listOf(genre),
				excludeItemIds = listOf(item.id),
				sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
				sortOrder = listOf(SortOrder.DESCENDING),
				limit = RELATED_LIMIT,
				fields = ItemRepository.browseFields,
				enableTotalRecordCount = false,
			)
			response.items
		}.onFailure { Timber.w(it, "FlixFin: genre fallback failed") }.getOrDefault(emptyList())
	}

	private companion object {
		const val CAST_LIMIT = 12
		const val EPISODE_LIMIT = 30
		const val RELATED_LIMIT = 12
	}
}
