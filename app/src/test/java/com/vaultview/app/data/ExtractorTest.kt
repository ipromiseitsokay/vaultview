package com.vaultview.app.data

import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractorTest {
    private val rom = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val cue = "FILE \"Game (Track 1).bin\" BINARY".toByteArray()

    private fun memorySink(out: MutableMap<String, ByteArray>) = FileSink { path ->
        val buf = ByteArrayOutputStream()
        object : FilterOutputStream(buf) { override fun close() { super.close(); out[path] = buf.toByteArray() } }
    }

    @Test fun unpacks7z() {
        val dir = Files.createTempDirectory("vv").toFile()
        val archive = File(dir, "Game.7z")
        SevenZOutputFile(archive).use { z ->
            for ((name, data) in listOf("Game (Track 1).bin" to rom, "Game.cue" to cue)) {
                val e = z.createArchiveEntry(File(dir, "x"), name)
                z.putArchiveEntry(e); z.write(data); z.closeArchiveEntry()
            }
        }
        val got = mutableMapOf<String, ByteArray>()
        val files = Extractor.extract(archive, memorySink(got))
        assertEquals(listOf("Game (Track 1).bin", "Game.cue"), files)
        assertArrayEquals(rom, got["Game (Track 1).bin"])
        assertArrayEquals(cue, got["Game.cue"])
    }

    @Test fun unpacksZipAndBlocksPathEscapes() {
        val dir = Files.createTempDirectory("vv").toFile()
        val archive = File(dir, "Game.zip")
        ZipOutputStream(archive.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("Game.sfc")); z.write(rom); z.closeEntry()
            z.putNextEntry(ZipEntry("../../evil.txt")); z.write(cue); z.closeEntry()
        }
        val got = mutableMapOf<String, ByteArray>()
        val files = Extractor.extract(archive, memorySink(got))
        assertEquals(listOf("Game.sfc", "evil.txt"), files)
        assertArrayEquals(rom, got["Game.sfc"])
        assertNull(Extractor.safePath("../.."))
    }
}
