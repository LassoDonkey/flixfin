package org.jellyfin.androidtv.flixfin

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The user-facing half of self-update: check quietly, ask once, install.
 *
 * Deliberately a plain [AlertDialog] rather than anything bespoke. It is
 * D-pad-navigable for free, it is the pattern the system installer will use two
 * screens later anyway, and it needs no layout work — which matters because the
 * update flow must keep working even if the rest of the UI is mid-rebuild.
 *
 * Rules this enforces, from docs/self-update.md:
 *  - Never during playback. Only called from startup, before anything plays.
 *  - Never silent. The user is asked, and Android asks again.
 *  - Never blocking. A failed or slow check just doesn't show a dialog.
 */
object FlixFinUpdatePrompt {
	/**
	 * Checks for an update and offers it. Safe to call unconditionally.
	 *
	 * Runs in the activity's lifecycle scope, so a user who walks away mid-check
	 * cancels it rather than getting a dialog thrown at a dead activity.
	 */
	fun checkAndOffer(activity: Activity, lifecycleOwner: LifecycleOwner) {
		lifecycleOwner.lifecycleScope.launch {
			val update = FlixFinUpdater.check() ?: return@launch
			if (activity.isFinishing || activity.isDestroyed) return@launch

			// Ask about the permission before downloading 50MB over TV Wi-Fi and
			// discovering we can't use it.
			if (!FlixFinUpdater.canInstall(activity)) {
				AlertDialog.Builder(activity)
					.setTitle("Update available — ${update.versionName}")
					.setMessage(
						"FlixFin can update itself, but Android needs your permission first.\n\n" +
							"Choose Allow, turn on the switch for FlixFin, then press Back."
					)
					.setPositiveButton("Allow") { _, _ ->
						FlixFinUpdater.requestInstallPermission(activity)
					}
					.setNegativeButton("Not now", null)
					.show()
				return@launch
			}

			val notes = update.notes.trim().takeIf { it.isNotEmpty() }
			AlertDialog.Builder(activity)
				.setTitle("Update available — ${update.versionName}")
				.setMessage(notes ?: "A new version of FlixFin is ready to install.")
				.setPositiveButton("Update") { _, _ -> startDownload(activity, lifecycleOwner, update) }
				.setNegativeButton("Later", null)
				.show()
		}
	}

	private fun startDownload(
		activity: Activity,
		lifecycleOwner: LifecycleOwner,
		update: FlixFinUpdater.Update,
	) {
		// A 50MB download over Fire TV Wi-Fi is slow enough that silence reads as
		// a crash. Indeterminate rather than a percentage bar, because the feed
		// does not always send a Content-Length.
		val progress = AlertDialog.Builder(activity)
			.setTitle("Downloading ${update.versionName}")
			.setMessage("This can take a minute. The TV will ask you to confirm before installing.")
			.setCancelable(false)
			.create()
		progress.show()

		lifecycleOwner.lifecycleScope.launch {
			val apk = FlixFinUpdater.download(activity, update)
			progress.dismiss()

			if (apk == null) {
				Toast.makeText(activity, "FlixFin update download failed", Toast.LENGTH_LONG).show()
				return@launch
			}

			val started = FlixFinUpdater.install(activity, apk)
			if (!started) {
				Timber.w("Install session did not start")
				Toast.makeText(activity, "FlixFin could not start the install", Toast.LENGTH_LONG).show()
			}
		}
	}
}
