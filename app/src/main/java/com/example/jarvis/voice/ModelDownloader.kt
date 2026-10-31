package com.example.jarvis.voice

import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream

/** Baixa e descompacta o modelo Vosk PT (~30 MB) uma única vez para filesDir/vosk-model. */
object ModelDownloader {
    private const val URL_MODEL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
    private const val MODEL_SHA256 = "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7"
    private const val MAX_UNPACKED_BYTES = 160L * 1024 * 1024

    fun download(filesDir: File, target: File): Boolean = try {
        val tmp = File(filesDir, "vosk-tmp").apply { deleteRecursively(); mkdirs() }
        val archive = File(filesDir, "vosk-model.zip")
        val conn = URL(URL_MODEL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.connect()
            if (conn.responseCode !in 200..299 || conn.contentLengthLong !in 1..80_000_000L) return false
            conn.inputStream.use { input -> archive.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        if (!sha256(archive).equals(MODEL_SHA256, ignoreCase = true)) {
            archive.delete()
            return false
        }
        var unpacked = 0L
        var entries = 0
        ZipInputStream(archive.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (++entries > 20_000) throw SecurityException("arquivo compactado inválido")
                val out = File(tmp, e.name)
                val base = tmp.canonicalPath + File.separator
                if (!out.canonicalPath.startsWith(base)) throw SecurityException("zip inválido")
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zin.read(buffer)
                            if (count < 0) break
                            unpacked += count
                            if (unpacked > MAX_UNPACKED_BYTES) throw SecurityException("modelo excede o limite")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                e = zin.nextEntry
            }
        }
        val inner = tmp.listFiles()?.singleOrNull { it.isDirectory } ?: tmp
        target.deleteRecursively()
        val ok = inner.renameTo(target)
        tmp.deleteRecursively()
        archive.delete()
        ok
    } catch (e: Exception) {
        false
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}
