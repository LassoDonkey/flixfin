package org.jellyfin.androidtv.flixfin.home

import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/**
 * Resolves artwork, asking for a type the item actually has.
 *
 * Jellyfin returns **404** for an image type an item lacks, so requesting
 * blindly produces a flash of broken artwork. Each kind therefore has an ordered
 * fallback, and the order is by how wrong the result looks:
 *
 *   - A Thumb standing in for a Backdrop is fine: both are 16:9, Thumb just has
 *     title text burned into it.
 *   - A **Primary standing in for either is a portrait poster stretched across a
 *     16:9 slot**, which is the single clearest tell of an amateur client. It is
 *     the last resort everywhere.
 *
 * ## maxWidth is not cosmetic
 *
 * It is the most important parameter here. Jellyfin will happily serve a 4K
 * source image, and tv-constraints.md §7 puts a Fire TV's *entire* graphics
 * budget at 30–40MB while a single 4K bitmap is ~33MB. Always ask for roughly
 * the size that will be drawn.
 *
 * Sizes below include headroom for the 1.08 focus scale and no more — Apple's
 * warning is that you must supply assets at the *focused* size or focused cards
 * look soft (visual-colour-type.md 3.5).
 */
class FlixFinImages(
	private val api: ApiClient,
) {
	fun url(item: BaseItemDto, kind: ImageKind): String? = when (kind) {
		ImageKind.Logo -> item.itemImages[ImageType.LOGO]
			?.getUrl(api, maxWidth = LOGO_WIDTH)

		ImageKind.Backdrop -> (
			item.itemImages[ImageType.BACKDROP]
				?: item.parentImages[ImageType.BACKDROP]
				?: item.itemImages[ImageType.THUMB]
				?: item.seriesThumbImage
				?: item.itemImages[ImageType.PRIMARY]
			)?.getUrl(api, maxWidth = BACKDROP_WIDTH)

		ImageKind.Thumb -> (
			item.itemImages[ImageType.THUMB]
				?: item.seriesThumbImage
				?: item.itemImages[ImageType.BACKDROP]
				// An episode's own Primary IS its still, and 16:9 — so for episodes
				// it belongs here rather than in the last-resort slot.
				?: item.itemImages[ImageType.PRIMARY]
				?: item.seriesPrimaryImage
			)?.getUrl(api, maxWidth = CARD_WIDTH)

		ImageKind.Primary -> (
			item.itemImages[ImageType.PRIMARY]
				?: item.seriesPrimaryImage
			)?.getUrl(api, maxWidth = POSTER_WIDTH)
	}

	private companion object {
		/** 152dp card at xhdpi is 304px; 340 covers the focus scale. */
		const val CARD_WIDTH = 340

		/** Full stage width. The one image allowed to be large. */
		const val BACKDROP_WIDTH = 1280

		const val LOGO_WIDTH = 640
		const val POSTER_WIDTH = 300
	}
}
