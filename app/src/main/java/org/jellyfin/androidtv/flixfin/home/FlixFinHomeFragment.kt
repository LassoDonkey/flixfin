package org.jellyfin.androidtv.flixfin.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jellyfin.androidtv.flixfin.ui.FlixFinText
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.flixfin.ui.RailItem
import org.jellyfin.androidtv.flixfin.ui.TopItem
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import org.koin.android.ext.android.inject
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * FlixFin's home screen.
 *
 * Replaces upstream's `HomeFragment`, which is a Compose toolbar wrapping a
 * Leanback rows fragment. This one is Compose throughout — see
 * [FlixFinHomeScreen] for why focus is driven by a cursor rather than by
 * Compose's own traversal.
 *
 * Kept as a new file rather than an edit to upstream's, so a rebase onto
 * jellyfin-androidtv stays a merge rather than a conflict. The only upstream
 * file touched is `Destinations`, which is one line.
 */
class FlixFinHomeFragment : Fragment() {
	private val viewModel by viewModel<FlixFinHomeViewModel>()
	private val images by inject<FlixFinImages>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val settingsViewModel by activityViewModel<SettingsViewModel>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = content {
		val state by viewModel.state.collectAsStateWithLifecycle()

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Colors.Background),
		) {
			when (val current = state) {
				is FlixFinHomeState.Loading -> {
					/*
					 * Deliberately nothing.
					 *
					 * A placeholder that appears and is replaced within a couple of
					 * frames reads as the screen glitching, and on a warm cache the
					 * library is back in well under the time it takes to notice. The
					 * background is already the right colour, so an empty frame here is
					 * indistinguishable from the app simply being fast.
					 */
				}

				is FlixFinHomeState.Empty -> FlixFinText(
					text = "Nothing to show yet. Check the server has a library.",
					size = Type.Body,
					color = Colors.TextDim,
					modifier = Modifier.align(Alignment.Center),
				)

				is FlixFinHomeState.Ready -> FlixFinHomeScreen(
					home = current.home,
					onOpen = { item ->
						navigationRepository.navigate(Destinations.itemDetails(item.id))
					},
					imageUrl = images::url,
					onRail = ::onRail,
					onTop = ::onTop,
				)
			}
		}
	}

	/**
	 * The rail: home, search, settings.
	 *
	 * Home is a no-op because you are already on it — the icon exists so the rail
	 * has a stable shape and an "you are here" state, not because pressing it
	 * should do anything.
	 */
	private fun onRail(item: RailItem) {
		when (item) {
			RailItem.Home -> Unit
			RailItem.Search -> navigationRepository.navigate(Destinations.search())
			/*
			 * Settings is neither an Activity nor a navigation destination upstream:
			 * it is an overlay driven by an activity-scoped view model, which is why
			 * this asks for it with `activityViewModel` rather than starting anything.
			 * Using the fragment scope here would get a second, unobserved instance
			 * and the press would do nothing.
			 */
			RailItem.Settings -> settingsViewModel.show()
		}
	}

	/**
	 * The top strip: the user's libraries.
	 *
	 * Resolved from the server's own views rather than hardcoded, because a
	 * library called "Films" on one server is "Movies" on another and may not
	 * exist at all. If the matching view is missing the press does nothing, which
	 * is better than navigating somewhere empty.
	 */
	private fun onTop(item: TopItem) {
		lifecycleScope.launch {
			val views = runCatching { userViewsRepository.views.first() }.getOrDefault(emptyList())
			val target = when (item) {
				TopItem.Films -> views.firstOrNull { it.collectionType == CollectionType.MOVIES }
				TopItem.Shows -> views.firstOrNull { it.collectionType == CollectionType.TVSHOWS }
				// No dedicated favourites view exists; the closest real thing is the
				// server's own favourites, which browse handles as a filter.
				TopItem.MyList -> views.firstOrNull()
			}
			if (target != null) {
				navigationRepository.navigate(Destinations.librarySmartScreen(target))
			} else {
				Timber.w("FlixFin: no library view matches $item")
			}
		}
	}

	override fun onResume() {
		super.onResume()
		/*
		 * Reload on return.
		 *
		 * Watch state is the whole point of the first row, and it changes while
		 * this fragment is stopped — you go away, watch something, come back. A
		 * "Continue watching" row that still lists what you just finished is worse
		 * than no row.
		 */
		viewModel.refresh()
	}
}
