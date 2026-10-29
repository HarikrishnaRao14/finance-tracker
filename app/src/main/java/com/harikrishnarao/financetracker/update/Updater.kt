package com.harikrishnarao.financetracker.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import com.harikrishnarao.financetracker.FinanceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What the latest GitHub Release says. */
data class UpdateInfo(val versionCode: Long, val versionName: String, val notes: String, val apkUrl: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    /** Android needs the user to allow "Install unknown apps" for Arthix once. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo?) : UpdateState
}

/**
 * Checks the latest GitHub Release when the app opens and installs it with one tap.
 *
 * Privacy: this is the app's only network use. It downloads two public files from the
 * project's own GitHub releases (version.json and the APK) and sends nothing about you.
 * Android only accepts the new APK if it is signed with the same key as the installed app.
 */
class Updater(private val context: Context) {
    companion object {
        const val VERSION_URL = "https://github.com/HarikrishnaRao14/finance-tracker/releases/latest/download/version.json"
        private const val CHECK_EVERY_MS = 30 * 60 * 1000L
        private const val TIMEOUT_MS = 15_000
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private var lastCheck = 0L

    val installedVersionCode: Long
        get() = context.packageManager.getPackageInfo(context.packageName, 0).let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        }

    val installedVersionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Called when the app opens or comes back to the front; checks at most every 30 minutes. */
    suspend fun checkIfDue() {
        if (Build.FINGERPRINT == "robolectric") return // no network in JVM tests
        val busy = _state.value is UpdateState.Downloading || _state.value is UpdateState.Installing
        if (busy || System.currentTimeMillis() - lastCheck < CHECK_EVERY_MS) return
        check(manual = false)
    }

    suspend fun check(manual: Boolean = true) {
        lastCheck = System.currentTimeMillis()
        if (manual) _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatching { fetchInfo() } }
        _state.value = result.fold(
            onSuccess = { info -> if (info.versionCode > installedVersionCode) UpdateState.Available(info) else UpdateState.UpToDate },
            onFailure = { if (manual) UpdateState.Failed("Couldn't reach GitHub. Check your internet and try again.", null) else UpdateState.Idle },
        )
    }

    private fun fetchInfo(): UpdateInfo {
        val json = JSONObject(get(VERSION_URL).bufferedReader().use { it.readText() })
        return UpdateInfo(
            versionCode = json.getLong("versionCode"),
            versionName = json.optString("versionName", "?"),
            notes = json.optString("notes", ""),
            apkUrl = json.getString("apk"),
        )
    }

    private fun get(url: String): java.io.InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.instanceFollowRedirects = true // GitHub redirects release downloads to its file servers
        c.setRequestProperty("Cache-Control", "no-cache")
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream
    }

    /** Download the APK and hand it to Android's installer. */
    suspend fun downloadAndInstall(info: UpdateInfo) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        val file = runCatching { withContext(Dispatchers.IO) { download(info) } }.getOrElse {
            _state.value = UpdateState.Failed("Download stopped. Check your internet and tap Update again.", info)
            return
        }
        _state.value = UpdateState.Installing(info)
        runCatching { withContext(Dispatchers.IO) { install(file) } }.onFailure {
            _state.value = UpdateState.Failed("Couldn't start the installer: ${it.message}", info)
        }
    }

    private fun download(info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the newest download
        val out = File(dir, "arthix-${info.versionCode}.apk")
        val c = URL(info.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.instanceFollowRedirects = true
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) _state.update { s -> if (s is UpdateState.Downloading) s.copy(progress = done.toFloat() / total) else s }
                }
            }
        }
        return out
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Once Arthix has installed itself one time, later updates on Android 12+ can skip the extra tap.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("arthix.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }

    /** Opens the system page where the user allows Arthix to install updates. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal fun onInstallFailed(message: String?) {
        val info = when (val s = _state.value) {
            is UpdateState.Installing -> s.info
            is UpdateState.Available -> s.info
            else -> null
        }
        _state.value = UpdateState.Failed(
            "Update not installed${message?.let { ": $it" } ?: ""}. If Play Protect blocked it, pause Play Protect and tap Update again.",
            info,
        )
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    /** Lets screenshot tests show the banner without the network. */
    @androidx.annotation.VisibleForTesting
    fun showForTest(state: UpdateState) { _state.value = state }
}

/** Receives the installer's answer; shows Android's confirm screen when it asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as FinanceApp).container.updater
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }.onFailure { updater.onInstallFailed(it.message) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app restarts as the new version
            else -> updater.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
