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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jellyfin.androidtv.flixfin.ui.FlixFinText
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Type
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.android.ext.android.inject
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
				)
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
