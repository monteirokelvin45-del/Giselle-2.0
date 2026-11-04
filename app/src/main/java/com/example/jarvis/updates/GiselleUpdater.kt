package com.example.jarvis.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Manifesto de atualizações, versionado neste repositório. Enquanto o ficheiro não existir
// (404), a verificação diz "sem atualização" em vez de falhar e tentar de novo.
const val MANIFEST_URL =
    "https://raw.githubusercontent.com/monteirokelvin45-del/Giselle-2.0/main/updates/manifest.json"

/** Hosts aceites em CADA salto (inclui redirecionamentos das Releases do GitHub). */
internal val ALLOWED_HOSTS = setOf(
    "raw.githubusercontent.com",
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

private const val CONFIG_SCHEMA = 1
private const val MAX_REDIRECTS = 5
private const val SURVIVAL_MS = 60_000L

// Áreas que NUNCA podem ser alteradas remotamente (exigem novo APK + confirmação).
internal val PROTECTED_KEYS = setOf("safetyGate", "permissions", "accessibility")

data class AppRelease(
    val versionCode: Long, val versionName: String,
    val apkUrl: String, val sha256: String, val notes: String,
)

data class ConfigRelease(
    val version: Int, val url: String, val sha256: String, val notes: String,
)

data class UpdateState(
    val appUpdate: AppRelease? = null,
    val configUpdate: ConfigRelease? = null,
    val installedConfigVersion: Int = 0,
)

/** Item 5: só oferece config mais nova que a instalada E que a última rejeitada/revertida. */
internal fun shouldOfferConfig(candidate: Int, installed: Int, rejected: Int): Boolean =
    candidate > installed && candidate > rejected

class GiselleUpdater(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("giselle_updates", Context.MODE_PRIVATE)
    private val configDir = File(ctx.filesDir, "config").apply { mkdirs() }
    private val current = File(configDir, "current.json")
    private val previous = File(configDir, "previous.json")

    var autoApplyConfig: Boolean
        get() = prefs.getBoolean("auto_config", true)
        set(v) = prefs.edit().putBoolean("auto_config", v).apply()

    // ---------- Estado instalado ----------
    private fun pkgInfo(flags: Int = 0): PackageInfo =
        ctx.packageManager.getPackageInfo(ctx.packageName, flags)

    fun installedAppVersion(): Long = pkgInfo().longVersionCode
    fun installedAppName(): String = pkgInfo().versionName ?: "?"
    fun installedConfigVersion(): Int = prefs.getInt("config_version", 0)
    internal fun rejectedConfigVersion(): Int = prefs.getInt("rejected_config_version", 0)

    /** A Giselle lê a configuração por aqui, no início de CADA ciclo do agente. */
    fun currentConfig(): JSONObject? =
        runCatching { JSONObject(current.readText()) }.getOrNull()

    // ---------- Verificação ----------
    suspend fun checkForUpdates(): UpdateState = withContext(Dispatchers.IO) {
        val raw = httpTextOrNull(MANIFEST_URL)
            ?: return@withContext UpdateState(installedConfigVersion = installedConfigVersion())
        val j = JSONObject(raw)
        val app = j.optJSONObject("app")?.let {
            AppRelease(
                it.getLong("versionCode"), it.getString("versionName"),
                it.getString("apkUrl"), it.getString("sha256"), it.optString("notes"),
            )
        }
        val cfg = j.optJSONObject("config")?.let {
            ConfigRelease(
                it.getInt("version"), it.getString("url"),
                it.getString("sha256"), it.optString("notes"),
            )
        }
        UpdateState(
            appUpdate = app?.takeIf { it.versionCode > installedAppVersion() },
            configUpdate = cfg?.takeIf {
                shouldOfferConfig(it.version, installedConfigVersion(), rejectedConfigVersion())
            },
            installedConfigVersion = installedConfigVersion(),
        )
    }

    // ---------- Camada 1: configuração (sem reinstalar) ----------
    suspend fun applyConfig(r: ConfigRelease): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(configDir, "download.tmp")
            try {
                download(r.url, tmp)
                commitConfig(tmp, r)
            } finally {
                tmp.delete()
            }
        }
    }

    /** Valida (hash, schema, chaves protegidas) e só então troca a config. Separado para ser testável. */
    internal fun commitConfig(tmp: File, r: ConfigRelease) {
        check(sha256(tmp).equals(r.sha256, ignoreCase = true)) { "Hash da configuração não confere" }
        val json = JSONObject(tmp.readText())
        check(json.optInt("schema", -1) == CONFIG_SCHEMA) { "Schema incompatível" }
        val bad = PROTECTED_KEYS.filter { json.has(it) }
        check(bad.isEmpty()) { "Pacote tenta alterar áreas protegidas: $bad" }

        if (current.exists()) current.copyTo(previous, overwrite = true)
        tmp.copyTo(current, overwrite = true)
        prefs.edit()
            .putInt("prev_config_version", installedConfigVersion())
            .putInt("config_version", r.version)
            .putBoolean("pending", true)
            .putInt("boot_attempts", 0)
            .apply()
    }

    /**
     * Volta à configuração anterior (ou aos valores embutidos no APK, se não houver anterior).
     * Item 5: memoriza a versão revertida para que o worker NÃO a reaplique em loop.
     */
    fun rollback() {
        val bad = installedConfigVersion()
        if (previous.exists()) {
            previous.copyTo(current, overwrite = true)
            previous.delete()
        } else {
            current.delete()
        }
        prefs.edit()
            .putInt("rejected_config_version", maxOf(bad, rejectedConfigVersion()))
            .putInt("config_version", prefs.getInt("prev_config_version", 0))
            .putBoolean("pending", false)
            .putInt("boot_attempts", 0)
            .apply()
    }

    /**
     * Chamar em Application.onCreate(). Item 6: uma abertura só conta como "arranque falhado"
     * se o processo morrer em menos de 60 s; se sobreviver, o contador é devolvido.
     * 3 arranques falhados seguidos com config pendente → rollback automático.
     */
    fun onAppStart() {
        if (!prefs.getBoolean("pending", false)) return
        val n = prefs.getInt("boot_attempts", 0) + 1
        if (n > 2) { rollback(); return }
        prefs.edit().putInt("boot_attempts", n).apply()
        Handler(Looper.getMainLooper()).postDelayed({
            if (prefs.getBoolean("pending", false)) {
                prefs.edit().putInt("boot_attempts", maxOf(0, prefs.getInt("boot_attempts", 1) - 1)).apply()
            }
        }, SURVIVAL_MS)
    }

    /** Chamado quando o agente completa um ciclo bem-sucedido com a config atual. */
    fun markHealthy() {
        if (!prefs.getBoolean("pending", false)) return
        prefs.edit().putBoolean("pending", false).putInt("boot_attempts", 0).apply()
    }

    // ---------- Camada 2: APK ----------
    suspend fun downloadApk(r: AppRelease, onProgress: (Int) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "giselle-${r.versionName}.apk")
            runCatching {
                download(r.apkUrl, apk, onProgress)
                check(sha256(apk).equals(r.sha256, ignoreCase = true)) { "Hash do APK não confere" }
                verifyApk(apk)
                apk
            }.onFailure { apk.delete() }
        }

    /** Garante: mesmo pacote, versão mais nova e MESMA assinatura do app instalado (API 28+). */
    private fun verifyApk(apk: File) {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: error("APK inválido")
        check(archive.packageName == ctx.packageName) { "APK de outro pacote" }
        check(archive.longVersionCode > installedAppVersion()) { "APK não é mais novo" }
        val a = certs(archive)
        check(a.isNotEmpty() && a == certs(pkgInfo(flags))) { "Assinatura diferente da instalada" }
    }

    private fun certs(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners
            ?.map { sha256(it.toByteArray()) }?.toSet() ?: emptySet()

    /** Abre o instalador do Android. Devolve false se faltar a permissão de "instalar apps desconhecidos". */
    fun install(apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        // Item 2: reaproveita o FileProvider único do app (authority ".fileprovider").
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    // ---------- Rede e hash ----------

    /**
     * Item 8: segue redirecionamentos À MÃO, validando HTTPS e a lista de hosts em cada salto
     * (as Releases do GitHub redirecionam para outro domínio).
     */
    private fun open(url: String): HttpURLConnection {
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            val u = URL(target)
            require(u.protocol == "https" && u.host in ALLOWED_HOSTS) { "URL não permitida: $target" }
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                require(!loc.isNullOrBlank()) { "Redirecionamento sem destino" }
                target = URL(u, loc).toString()
            } else {
                return c
            }
        }
        error("Redirecionamentos a mais")
    }

    /** Devolve null em 404 (manifesto ainda não publicado); outros erros continuam a lançar exceção. */
    private fun httpTextOrNull(url: String): String? {
        val c = open(url)
        return try {
            if (c.responseCode == 404) return null
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun httpText(url: String): String {
        val c = open(url)
        return try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit = {}) {
        val c = open(url)
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val b = ByteArray(16 * 1024)
            while (true) {
                val n = s.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
