package org.jellyfin.androidtv.reef

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
 * Reef is sideloaded — there is no store to push updates through, and walking to
 * the telly with a laptop and `adb` every time is exactly the friction this is
 * meant to remove. So the app checks a release feed, downloads the APK and hands
 * it to Android's own installer.
 *
 * Google Play forbids apps updating themselves. We are not on Play, so the rule
 * does not apply — but everything Play is protecting against is real, hence the
 * constraints below.
 *
 * ## Why plain HTTP is acceptable here, when it normally is not
 *
 * [UPDATE_HOST] is a Tailscale address. Every byte between this TV and the Gitea
 * box is inside a WireGuard tunnel: encrypted, and the peer is cryptographically
 * authenticated by its node key. An attacker cannot reach that address without
 * already being on the tailnet.
 *
 * That is the threat HTTPS would be mitigating, and it is already mitigated a
 * layer down. The backstop is Android itself: an update only installs if it is
 * signed with the same key as the installed app, so even a swapped APK cannot
 * replace Reef. See docs/networking.md and docs/self-update.md.
 *
 * The host is a hardcoded constant on purpose. If it were configurable, anything
 * that could write config could point the updater at an APK of its choosing —
 * which turns a convenience feature into a remote code execution path.
 */
object ReefUpdater {
	/** Tailnet address of the Gitea instance. Not configurable — see class docs. */
	private const val UPDATE_HOST = "http://100.101.42.43:3000"
	private const val REPO_OWNER = "LassoDonkey"
	private const val REPO_NAME = "reef-tv"

	private const val RELEASES_URL = "$UPDATE_HOST/api/v1/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

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
						asset["browser_download_url"]?.jsonPrimitive?.content?.let(::pinToUpdateHost)
					} else {
						null
					}
				} ?: return@runCatching null

			val remoteCode = versionCodeOf(tag)
			if (remoteCode <= BuildConfig.VERSION_CODE) {
				Timber.i("Reef is up to date (local=${BuildConfig.VERSION_CODE}, remote=$remoteCode)")
				return@runCatching null
			}

			Timber.i("Reef update available: $tag (code $remoteCode)")
			Update(tag, remoteCode, apkUrl, notes)
		}.onFailure { error ->
			// Expected whenever the TV is off the tailnet. Log, never surface.
			Timber.d(error, "Reef update check failed")
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

	/**
	 * Rewrites an asset URL to go through [UPDATE_HOST].
	 *
	 * Gitea builds `browser_download_url` from its own configured base URL, which
	 * here is the MagicDNS name `odin.tail8da5e5.ts.net:3000` rather than the
	 * `100.x` address. That works only while MagicDNS is resolving on the device —
	 * it is on by default, but it is a setting, and a Fire TV with it off would
	 * fail every download while the version check kept succeeding. A silent,
	 * intermittent update failure is a miserable thing to debug from the sofa.
	 *
	 * Pinning the host also closes a smaller hole: without it, the file we fetch
	 * is wherever the server's config points, not necessarily the host we decided
	 * to trust. Path and query are preserved; only scheme/host/port are replaced.
	 *
	 * Falls back to the original URL if it cannot be parsed, so a Gitea upgrade
	 * that changes the URL shape degrades to "might work" rather than "definitely
	 * broken".
	 */
	private fun pinToUpdateHost(url: String): String = runCatching {
		val parsed = URL(url)
		val suffix = buildString {
			append(parsed.path)
			parsed.query?.let { append("?").append(it) }
		}
		"$UPDATE_HOST$suffix"
	}.getOrDefault(url)

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
			val target = File(context.cacheDir, "reef-update-${update.versionCode}.apk")
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

			Timber.i("Downloaded Reef update to $target (${target.length()} bytes)")
			target
		}.onFailure { error ->
			Timber.w(error, "Reef update download failed")
		}.getOrNull()
	}

	/**
	 * True when this app is allowed to trigger an install.
	 *
	 * Since API 26 the user must grant "install unknown apps" to Reef
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
	 * privileges Reef neither has nor should want.
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
				session.openWrite("reef", 0, apk.length()).use { output ->
					apk.inputStream().use { input -> input.copyTo(output) }
					session.fsync(output)
				}

				val intent = Intent(context, ReefUpdateReceiver::class.java)
				val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
					android.app.PendingIntent.FLAG_MUTABLE
				val pending = android.app.PendingIntent.getBroadcast(context, sessionId, intent, flags)

				session.commit(pending.intentSender)
			}

			Timber.i("Reef update session $sessionId committed")
			true
		}.onFailure { error ->
			Timber.e(error, "Reef update install failed")
		}.getOrDefault(false)
	}

	private fun String.toUri() = android.net.Uri.parse(this)
}
