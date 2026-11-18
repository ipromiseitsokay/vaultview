package com.vaultview.app.data

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

/** Somewhere to write extracted files; paths use '/' between folders. */
fun interface FileSink {
    fun create(path: String): OutputStream
}

/** Unpacks .zip and .7z archives (the formats vimm.net serves). */
object Extractor {
    fun canExtract(name: String) = name.endsWith(".zip", true) || name.endsWith(".7z", true)

    /** Returns the relative paths of the files written. */
    fun extract(
        archive: File,
        sink: FileSink,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): List<String> {
        if (NativeExtractor.available) {
            try {
                return NativeExtractor.extract(archive, sink, onProgress, isCancelled)
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Throwable) {
                // Fall through to the Java unpacker; it overwrites anything half-written.
                android.util.Log.w("Extractor", "Native unpack failed, using fallback", e)
            }
        }
        return extractJava(archive, sink, onProgress, isCancelled)
    }

    /** Pure-Java unpacking: slower, but works everywhere (and in unit tests). */
    fun extractJava(
        archive: File,
        sink: FileSink,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): List<String> = when {
        archive.name.endsWith(".zip", true) -> extractZip(archive, sink, onProgress, isCancelled)
        archive.name.endsWith(".7z", true) -> extract7z(archive, sink, onProgress, isCancelled)
        else -> throw IOException("Not an archive: ${archive.name}")
    }

    /** Drops "..", ".", drive letters and empty segments so entries can't escape the folder. */
    fun safePath(name: String): String? =
        name.replace('\\', '/').split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." && !it.endsWith(":") }
            .joinToString("/").ifEmpty { null }

    private fun extractZip(archive: File, sink: FileSink, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): List<String> {
        val written = mutableListOf<String>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory }
            val total = entries.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
            var done = 0L
            for (e in entries) {
                val path = safePath(e.name) ?: continue
                zip.getInputStream(e).use { input ->
                    java.io.BufferedOutputStream(sink.create(path), 1 shl 20).use { out -> done = copy(input, out, done, total, onProgress, isCancelled) }
                }
                written += path
            }
        }
        return written
    }

    private fun extract7z(archive: File, sink: FileSink, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): List<String> {
        val written = mutableListOf<String>()
        SevenZFile.builder().setFile(archive).get().use { sz ->
            val total = sz.entries.filter { !it.isDirectory }.sumOf { it.size }.coerceAtLeast(1)
            var done = 0L
            var entry = sz.nextEntry
            val buf = ByteArray(1 shl 20)
            while (entry != null) {
                if (!entry.isDirectory) {
                    val path = safePath(entry.name)
                    if (path != null) {
                        java.io.BufferedOutputStream(sink.create(path), 1 shl 20).use { out ->
                            while (true) {
                                if (isCancelled()) throw InterruptedException("Cancelled")
                                val n = sz.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                onProgress(done.toFloat() / total)
                            }
                        }
                        written += path
                    }
                }
                entry = sz.nextEntry
            }
        }
        return written
    }

    private fun copy(input: InputStream, out: OutputStream, start: Long, total: Long,
                     onProgress: (Float) -> Unit, isCancelled: () -> Boolean): Long {
        val buf = ByteArray(1 shl 20)
        var done = start
        while (true) {
            if (isCancelled()) throw InterruptedException("Cancelled")
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            onProgress(done.toFloat() / total)
        }
        return done
    }
}
