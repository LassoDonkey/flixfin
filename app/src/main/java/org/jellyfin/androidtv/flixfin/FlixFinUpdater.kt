package org.jellyfin.androidtv.flixfin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.BuildConfig
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update against the Gitea release feed.
 *
 * FlixFin is sideloaded — there is no store to push updates through, and walking to
 * the telly with a laptop and `adb` every time is exactly the friction this is
 * meant to remove. So the app checks a release feed, downloads the APK and hands
 * it to Android's own installer.
 *
 * Google Play forbids apps updating themselves. We are not on Play, so the rule
 * does not apply — but everything Play is protecting against is real, hence the
 * constraints below.
 *
 * ## Transport and trust
 *
 * [API_HOST] is HTTPS, so the feed and the download are both authenticated in
 * transport. This used to be plain HTTP to a Tailscale address, which was
 * defensible — every byte was inside a WireGuard tunnel with a cryptographically
 * authenticated peer — but only for TVs on that tailnet, and it put a private
 * address in a public repository.
 *
 * The backstop is Android itself, and it is the one that actually matters: an
 * update installs only if it is signed with the same key as the installed app,
 * so even a substituted APK cannot replace FlixFin. See docs/self-update.md.
 *
 * The host is a hardcoded constant on purpose. If it were configurable, anything
 * that could write config could point the updater at an APK of its choosing —
 * which turns a convenience feature into a remote code execution path.
 */
object FlixFinUpdater {
	/*
	 * GitHub Releases, not the tailnet Gitea.
	 *
	 * It used to point at `http://100.101.42.43:3000`, a Tailscale address, which
	 * meant a TV not on the tailnet passed the check and then failed every
	 * download — silently, because a failed check is deliberately swallowed so
	 * startup never hangs. Anyone the APK was sent to would sit on an old build
	 * forever with nothing on screen to say so.
	 *
	 * GitHub also removes a private address from a public repository, and it is
	 * HTTPS rather than plaintext HTTP.
	 *
	 * Still a hardcoded constant, and that part is not negotiable: if the update
	 * host were configurable, anything able to write config could point the
	 * updater at an APK of its choosing, which turns a convenience feature into a
	 * remote code execution path. The signature check is the backstop — Android
	 * refuses an update not signed with the installed app's key — but the host
	 * should never have been the weak link in the first place.
	 */
	private const val API_HOST = "https://api.github.com"
	private const val REPO_OWNER = "LassoDonkey"
	private const val REPO_NAME = "flixfin"

	private const val RELEASES_URL = "$API_HOST/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

	/**
	 * Short. A TV that is off the tailnet must not make startup hang while it
	 * waits — an update check is never worth delaying the home screen for.
	 */
	private const val CONNECT_TIMEOUT_MS = 3_000
	private const val READ_TIMEOUT_MS = 10_000

	private val json = Json { ignoreUnknownKeys = true }

	data class Update(
		val versionName: String,
		val versionCode: Int,
		val apkUrl: String,
		val notes: String,
	)

	/**
	 * Returns an [Update] if the feed advertises a newer versionCode, else null.
	 *
	 * Never throws. A failed check is not an error worth surfacing — the TV may
	 * simply be off the tailnet, and a scary dialog about that would be noise.
	 */
	suspend fun check(): Update? = withContext(Dispatchers.IO) {
		runCatching {
			val body = fetch(RELEASES_URL) ?: return@runCatching null
			val release = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null

			// Gitea tags releases "v1.2.3"; upstream's version scheme drops the v.
			val tag = release["tag_name"]?.jsonPrimitive?.content?.removePrefix("v")
				?: return@runCatching null
			val notes = release["body"]?.jsonPrimitive?.content.orEmpty()

			val assets = release["assets"] as? JsonArray ?: return@runCatching null
			val apkUrl = assets
				.filterIsInstance<JsonObject>()
				.firstNotNullOfOrNull { asset ->
					val name = asset["name"]?.jsonPrimitive?.content
					if (name?.endsWith(".apk") == true) {
						asset["browser_download_url"]?.jsonPrimitive?.content
					} else {
						null
					}
				} ?: return@runCatching null

			val remoteCode = versionCodeOf(tag)
			if (remoteCode <= BuildConfig.VERSION_CODE) {
				Timber.i("FlixFin is up to date (local=${BuildConfig.VERSION_CODE}, remote=$remoteCode)")
				return@runCatching null
			}

			Timber.i("FlixFin update available: $tag (code $remoteCode)")
			Update(tag, remoteCode, apkUrl, notes)
		}.onFailure { error ->
			// Expected whenever the TV is off the tailnet. Log, never surface.
			Timber.d(error, "FlixFin update check failed")
		}.getOrNull()
	}

	/**
	 * Mirrors `getVersionCode` in buildSrc/VersionUtils.kt.
	 *
	 * MAJOR*1000000 + MINOR*10000 + PATCH*100 + prerelease (99 when absent, so a
	 * final release always outranks its own release candidates).
	 *
	 * Kept deliberately tolerant: a malformed tag yields a code that loses the
	 * comparison rather than throwing, so a bad release can never brick the
	 * update check for every device.
	 */
	private fun versionCodeOf(versionName: String): Int = runCatching {
		val dash = versionName.indexOf('-')
		val core = if (dash == -1) versionName else versionName.substring(0, dash)
		val pre = if (dash == -1) null else versionName.substring(dash + 1)

		val parts = core.split('.').mapNotNull(String::toIntOrNull)
		val major = parts.getOrElse(0) { 0 }
		val minor = parts.getOrElse(1) { 0 }
		val patch = parts.getOrElse(2) { 0 }
		val build = pre?.substringAfter('.')?.toIntOrNull() ?: 99

		major * 1_000_000 + minor * 10_000 + patch * 100 + build
	}.getOrDefault(0)

	/*
	 * `pinToUpdateHost` is gone with the move to GitHub.
	 *
	 * It existed because Gitea returns `browser_download_url` using its MagicDNS
	 * name (`odin.tail8da5e5.ts.net`) rather than the 100.x address, so a TV with
	 * MagicDNS off passed the version check and then failed every download — a
	 * silent, intermittent failure that was horrible to diagnose from the sofa,
	 * and the entire reason v1.0.1 existed.
	 *
	 * GitHub serves asset URLs on its own public hostnames, which resolve
	 * everywhere. Rewriting them would break the download rather than fix it.
	 */
	private fun fetch(url: String): String? {
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			connectTimeout = CONNECT_TIMEOUT_MS
			readTimeout = READ_TIMEOUT_MS
			requestMethod = "GET"
			setRequestProperty("Accept", "application/json")
		}
		return try {
			if (connection.responseCode !in 200..299) {
				Timber.d("Update feed returned HTTP ${connection.responseCode}")
				null
			} else {
				connection.inputStream.bufferedReader().use { it.readText() }
			}
		} finally {
			connection.disconnect()
		}
	}

	/**
	 * Downloads the APK into app-private cache and returns it.
	 *
	 * Cache, not external storage: no extra permission needed, and Android can
	 * reclaim it if the device runs short. [onProgress] reports 0..1, or -1 when
	 * the server sends no Content-Length — Fire TV Wi-Fi is slow enough that a
	 * silent download looks like a hang, so the caller must show something.
	 */
	suspend fun download(
		context: Context,
		update: Update,
		onProgress: (Float) -> Unit = {},
	): File? = withContext(Dispatchers.IO) {
		runCatching {
			val target = File(context.cacheDir, "flixfin-update-${update.versionCode}.apk")
			if (target.exists()) target.delete()

			val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
				connectTimeout = CONNECT_TIMEOUT_MS
				readTimeout = READ_TIMEOUT_MS
			}

			try {
				if (connection.responseCode !in 200..299) {
					Timber.w("Update download returned HTTP ${connection.responseCode}")
					return@runCatching null
				}

				val total = connection.contentLengthLong
				var read = 0L

				connection.inputStream.use { input ->
					target.outputStream().use { output ->
						val buffer = ByteArray(64 * 1024)
						while (true) {
							val count = input.read(buffer)
							if (count == -1) break
							output.write(buffer, 0, count)
							read += count
							onProgress(if (total > 0) read.toFloat() / total else -1f)
						}
					}
				}
			} finally {
				connection.disconnect()
			}

			Timber.i("Downloaded FlixFin update to $target (${target.length()} bytes)")
			target
		}.onFailure { error ->
			Timber.w(error, "FlixFin update download failed")
		}.getOrNull()
	}

	/**
	 * True when this app is allowed to trigger an install.
	 *
	 * Since API 26 the user must grant "install unknown apps" to FlixFin
	 * specifically — declaring REQUEST_INSTALL_PACKAGES is not enough. Check this
	 * BEFORE downloading, or the app pulls 50MB over TV Wi-Fi and then dead-ends
	 * on a permission screen.
	 */
	fun canInstall(context: Context): Boolean =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.packageManager.canRequestPackageInstalls()
		} else {
			true
		}

	/** Sends the user to the system screen where that permission is granted. */
	fun requestInstallPermission(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		runCatching {
			context.startActivity(
				Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
					.setData("package:${context.packageName}".toUri())
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
		}.onFailure { Timber.w(it, "Could not open unknown-sources settings") }
	}

	/**
	 * Hands the APK to Android's package installer.
	 *
	 * The system shows its own confirmation, which is navigable with a D-pad and
	 * is not something we should try to bypass. Silent install needs device-owner
	 * privileges FlixFin neither has nor should want.
	 *
	 * If the downloaded APK is signed with a different key than the installed
	 * app, Android rejects it here. That is the backstop that makes plain HTTP
	 * over the tailnet acceptable.
	 */
	suspend fun install(context: Context, apk: File): Boolean = withContext(Dispatchers.IO) {
		runCatching {
			val installer = context.packageManager.packageInstaller
			val params = PackageInstaller.SessionParams(
				PackageInstaller.SessionParams.MODE_FULL_INSTALL
			)
			val sessionId = installer.createSession(params)

			installer.openSession(sessionId).use { session ->
				session.openWrite("flixfin", 0, apk.length()).use { output ->
					apk.inputStream().use { input -> input.copyTo(output) }
					session.fsync(output)
				}

				val intent = Intent(context, FlixFinUpdateReceiver::class.java)
				val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
					android.app.PendingIntent.FLAG_MUTABLE
				val pending = android.app.PendingIntent.getBroadcast(context, sessionId, intent, flags)

				session.commit(pending.intentSender)
			}

			Timber.i("FlixFin update session $sessionId committed")
			true
		}.onFailure { error ->
			Timber.e(error, "FlixFin update install failed")
		}.getOrDefault(false)
	}

	private fun String.toUri() = android.net.Uri.parse(this)
}
