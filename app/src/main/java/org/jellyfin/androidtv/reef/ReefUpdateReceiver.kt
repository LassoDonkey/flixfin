package org.jellyfin.androidtv.reef

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import timber.log.Timber

/**
 * Receives the outcome of a [ReefUpdater] install session.
 *
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] is the normal first response,
 * not a failure: the system is telling us it wants to show its own confirmation
 * dialog, and hands back an Intent to launch. Miss this and the update silently
 * does nothing, which is the classic way self-update gets shipped broken.
 */
class ReefUpdateReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				Timber.i("Update needs user confirmation, launching system prompt")

				@Suppress("DEPRECATION")
				val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
				if (confirm == null) {
					Timber.e("Install prompt intent missing")
					return
				}
				confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				context.startActivity(confirm)
			}

			PackageInstaller.STATUS_SUCCESS -> {
				Timber.i("Reef updated successfully")
			}

			else -> {
				val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
				Timber.w("Reef update failed: status=$status message=$message")
				// Worth surfacing: the user explicitly asked for this update and
				// otherwise gets no feedback at all.
				Toast.makeText(context, "Reef update failed: $message", Toast.LENGTH_LONG).show()
			}
		}
	}
}
