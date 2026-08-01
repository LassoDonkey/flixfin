package org.jellyfin.androidtv.flixfin.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

class FlixFinDetailViewModel(
	private val repository: FlixFinDetailRepository,
) : ViewModel() {
	private val _detail = MutableStateFlow<FlixFinDetail?>(null)
	val detail: StateFlow<FlixFinDetail?> = _detail.asStateFlow()

	private var itemId: UUID? = null

	fun load(id: UUID, season: Int? = null) {
		itemId = id
		viewModelScope.launch {
			_detail.value = runCatching { repository.load(id, season) }
				.onFailure { Timber.e(it, "FlixFin: detail load failed") }
				.getOrNull()
		}
	}

	/**
	 * Reload for a different season.
	 *
	 * Goes back through the repository rather than caching every season's episodes
	 * up front: a long-running series is dozens of requests for episodes nobody
	 * opened, on a device where each one costs.
	 */
	fun selectSeason(number: Int) {
		itemId?.let { load(it, number) }
	}

	/** Watch state changes while the player is up, so the page is stale on return. */
	fun refresh() {
		itemId?.let { load(it, _detail.value?.episodes?.firstOrNull()?.parentIndexNumber) }
	}
}
