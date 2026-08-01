package org.jellyfin.androidtv.flixfin.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jellyfin.androidtv.flixfin.home.FlixFinImages
import org.jellyfin.androidtv.flixfin.ui.FlixFinTheme.Colors
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.util.UUID

/**
 * FlixFin's detail page.
 *
 * Reads the same `ItemId` extra upstream's `FullDetailsFragment` does, so both
 * are drop-in for each other and `Destinations.itemDetails` needs no change
 * beyond the class it names.
 */
class FlixFinDetailFragment : Fragment() {
	private val viewModel by viewModel<FlixFinDetailViewModel>()
	private val images by inject<FlixFinImages>()
	private val api by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val playbackHelper by inject<PlaybackHelper>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = content {
		val detail by viewModel.detail.collectAsStateWithLifecycle()

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Colors.BackgroundDeep),
		) {
			detail?.let { current ->
				FlixFinDetailScreen(
					detail = current,
					imageUrl = images::url,
					personImageUrl = { person ->
						person.primaryImage?.getUrl(api, maxWidth = CAST_IMAGE_WIDTH)
					},
					onPlay = { item ->
						playbackHelper.retrieveAndPlay(item.id, false, requireContext())
					},
					onTrailer = ::openTrailer,
					onOpen = { item ->
						navigationRepository.navigate(Destinations.itemDetails(item.id))
					},
					onSeason = viewModel::selectSeason,
					onBack = { navigationRepository.goBack() },
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val id = arguments?.getString(EXTRA_ITEM_ID)?.let(UUID::fromString)
		if (id == null) {
			Timber.e("FlixFin: detail opened with no ItemId")
			navigationRepository.goBack()
			return
		}
		viewModel.load(id)
	}

	override fun onResume() {
		super.onResume()
		// Watch state changes while the player is up, so this is stale on return —
		// a Resume button that still says Play is the visible symptom.
		viewModel.refresh()
	}

	/**
	 * Hands the trailer to whatever can play it.
	 *
	 * The prototype embedded YouTube in an iframe, which does not exist on Android
	 * TV. An intent lets the YouTube app take it if installed, and any browser
	 * otherwise; if nothing can, we say so rather than failing silently.
	 */
	private fun openTrailer(url: String) {
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
		runCatching { startActivity(intent) }
			.onFailure { Timber.w(it, "FlixFin: nothing can open the trailer URL") }
	}

	companion object {
		/** Matches upstream's FullDetailsFragment so the two are interchangeable. */
		const val EXTRA_ITEM_ID = "ItemId"

		private const val CAST_IMAGE_WIDTH = 160
	}
}
