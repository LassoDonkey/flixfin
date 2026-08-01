package org.jellyfin.androidtv.flixfin.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface FlixFinHomeState {
	data object Loading : FlixFinHomeState
	data class Ready(val home: FlixFinHome) : FlixFinHomeState

	/**
	 * The server answered with nothing usable.
	 *
	 * Distinct from [Loading] on purpose: a spinner that never resolves is the
	 * worst of the three states, because it gives no reason and no way forward.
	 */
	data object Empty : FlixFinHomeState
}

class FlixFinHomeViewModel(
	private val repository: FlixFinHomeRepository,
) : ViewModel() {
	private val _state = MutableStateFlow<FlixFinHomeState>(FlixFinHomeState.Loading)
	val state: StateFlow<FlixFinHomeState> = _state.asStateFlow()

	init {
		refresh()
	}

	fun refresh() {
		viewModelScope.launch {
			val home = runCatching { repository.load() }
				.onFailure { Timber.e(it, "FlixFin: home load failed") }
				.getOrNull()

			_state.value = when {
				home == null -> FlixFinHomeState.Empty
				home.rows.isEmpty() && home.featured.isEmpty() -> FlixFinHomeState.Empty
				else -> FlixFinHomeState.Ready(home)
			}
		}
	}
}
