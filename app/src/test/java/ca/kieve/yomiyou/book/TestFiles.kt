package ca.kieve.yomiyou.book

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Enriched EPUBs and voice files made in the test, every entry stored as yomiage stores them. */
object TestFiles {
    val cover = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for ((name, data) in entries) {
                out.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    crc = CRC32().apply { update(data) }.value
                })
                out.write(data)
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    fun epub(bookId: String, title: String, phraseData: Boolean = true): ByteArray {
        val container = """<?xml version="1.0"?><container version="1.0"
            xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><manifest>
            <item id="cover" href="images/cover.png" media-type="image/png" properties="cover-image"/>
            <item id="text" href="text.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine page-progression-direction="rtl"><itemref idref="text"/></spine></package>"""
        val manifest = """{"format":1,"bookId":"$bookId","title":"$title","phrases":10,
            "voices":[{"id":"ginga","label":"銀芽 (通常)","credit":"AI声優-銀芽"},{"id":"mao","label":"まお (ノーマル)"}],
            "chapters":[]}"""
        val entries = listOfNotNull(
            "mimetype" to "application/epub+zip".encodeToByteArray(),
            "META-INF/container.xml" to container.encodeToByteArray(),
            "OEBPS/content.opf" to opf.encodeToByteArray(),
            "OEBPS/text.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body/></html>".encodeToByteArray(),
            "OEBPS/images/cover.png" to cover,
            if (phraseData) "OEBPS/yomiage.json" to manifest.encodeToByteArray() else null,
        )
        return zip(*entries.toTypedArray())
    }

    fun voice(bookId: String, voice: String, label: String): ByteArray = zip(
        "voice.json" to """{"format":1,"bookId":"$bookId","voice":"$voice","label":"$label","credit":"",
            "segments":[],"chapters":[]}""".encodeToByteArray()
    )
}
