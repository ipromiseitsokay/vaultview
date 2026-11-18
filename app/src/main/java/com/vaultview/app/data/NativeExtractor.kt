package com.vaultview.app.data

import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Unpacks with the real 7-Zip engine (native code via 7-Zip-JBinding-4Android), which is several
 * times faster than the pure-Java decoders, especially for the LZMA2 .7z files vimm.net uses for
 * disc games. Everything is unpacked in one pass, so solid archives aren't decoded twice.
 */
object NativeExtractor {
    /** False on the desktop JVM (unit tests) or if the native library couldn't load. */
    val available: Boolean by lazy {
        runCatching { SevenZip.getSevenZipVersion(); true }.getOrDefault(false)
    }

    fun extract(
        archive: File,
        sink: FileSink,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): List<String> {
        val written = mutableListOf<String>()
        RandomAccessFile(archive, "r").use { raf ->
            val inArchive = SevenZip.openInArchive(null, RandomAccessFileInStream(raf))
            try {
                val count = inArchive.numberOfItems
                val paths = arrayOfNulls<String>(count)
                for (i in 0 until count) {
                    val folder = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                    if (!folder) paths[i] = Extractor.safePath(inArchive.getProperty(i, PropID.PATH) as? String ?: "")
                }
                val wanted = (0 until count).filter { paths[it] != null }.toIntArray()

                var current: OutputStream? = null
                var total = 1L
                var failure: Throwable? = null
                fun closeCurrent() { current?.close(); current = null }

                val callback = object : IArchiveExtractCallback {
                    override fun setTotal(t: Long) { total = t.coerceAtLeast(1) }
                    override fun setCompleted(done: Long) { onProgress(done.toFloat() / total) }
                    override fun prepareOperation(mode: ExtractAskMode) {}

                    override fun getStream(index: Int, mode: ExtractAskMode): ISequentialOutStream? {
                        if (mode != ExtractAskMode.EXTRACT) return null
                        val path = paths[index] ?: return null
                        try {
                            closeCurrent()
                            val out = BufferedOutputStream(sink.create(path), 1 shl 20)
                            current = out
                            written += path
                            return ISequentialOutStream { data ->
                                if (isCancelled()) { failure = InterruptedException("Cancelled"); throw SevenZipException("Cancelled") }
                                try { out.write(data) } catch (e: IOException) { failure = e; throw SevenZipException(e.message) }
                                data.size
                            }
                        } catch (e: IOException) {
                            failure = e
                            throw SevenZipException(e.message)
                        }
                    }

                    override fun setOperationResult(result: ExtractOperationResult) {
                        closeCurrent()
                        if (result != ExtractOperationResult.OK) throw SevenZipException("Archive is damaged ($result)")
                    }
                }

                try {
                    inArchive.extract(wanted, false, callback)
                } catch (e: SevenZipException) {
                    runCatching { closeCurrent() }
                    throw failure ?: IOException(e.message ?: "Couldn't unpack", e)
                } finally {
                    runCatching { closeCurrent() }
                }
            } finally {
                inArchive.close()
            }
        }
        return written
    }
}
