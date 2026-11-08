package ca.kieve.yomiyou.book

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * A zip's table of contents, read from its end, and its entries read on demand: an EPUB, or a voice file of several
 * gigabytes (zip64) whose audio is read from where it lies.
 */
class ZipIndex private constructor(private val source: RandomAccess, private val entries: Map<String, Entry>) :
    Closeable {

    class Entry(val name: String, val method: Int, val compressedSize: Long, val size: Long, val headerOffset: Long)

    val names: Set<String> get() = entries.keys

    fun has(name: String): Boolean = name in entries

    fun read(name: String): ByteArray {
        val entry = entries[name] ?: throw IOException("No $name in the zip")
        if (entry.size > Int.MAX_VALUE || entry.compressedSize > Int.MAX_VALUE) throw IOException("$name is too big")
        val data = ByteArray(entry.compressedSize.toInt())
        source.readFully(dataOffset(entry), ByteBuffer.wrap(data))
        return when (entry.method) {
            STORED -> data
            DEFLATED -> inflate(data, entry.size.toInt())
            else -> throw IOException("$name is compressed in a way this reader doesn't know")
        }
    }

    fun text(name: String): String = read(name).decodeToString()

    /** Where an uncompressed entry's bytes lie in the file, to be read as they are (a voice file's audio). */
    fun storedRange(name: String): LongRange {
        val entry = entries[name] ?: throw IOException("No $name in the zip")
        if (entry.method != STORED) throw IOException("$name is compressed")
        val start = dataOffset(entry)
        return start until start + entry.size
    }

    /** Reads bytes from the file the index was opened on: where [storedRange] points. */
    fun readFully(position: Long, into: ByteBuffer) = source.readFully(position, into)

    private fun dataOffset(entry: Entry): Long {
        val header = buffer(LOCAL_HEADER_SIZE)
        source.readFully(entry.headerOffset, header)
        header.flip()
        if (header.getInt(0) != LOCAL_HEADER) throw IOException("Damaged zip at ${entry.name}")
        return entry.headerOffset + LOCAL_HEADER_SIZE + header.u16(26) + header.u16(28)
    }

    override fun close() = source.close()

    companion object {
        private const val STORED = 0
        private const val DEFLATED = 8
        private const val LOCAL_HEADER = 0x04034b50
        private const val LOCAL_HEADER_SIZE = 30
        private const val CENTRAL_HEADER = 0x02014b50
        private const val END = 0x06054b50
        private const val END_SIZE = 22
        private const val ZIP64_LOCATOR = 0x07064b50
        private const val ZIP64_END = 0x06064b50
        private const val MAX_COMMENT = 0xFFFF
        private const val U16_MAX = 0xFFFFL
        private const val U32_MAX = 0xFFFFFFFFL

        /** @throws IOException when it isn't a zip */
        fun open(source: RandomAccess): ZipIndex {
            val tailSize = minOf(source.size, (END_SIZE + MAX_COMMENT).toLong()).toInt()
            val tail = buffer(tailSize)
            source.readFully(source.size - tailSize, tail)
            tail.flip()
            val end = (tailSize - END_SIZE downTo 0).firstOrNull { tail.getInt(it) == END }
                ?: throw IOException("Not a zip")
            var count = tail.u16(end + 10).toLong()
            var directorySize = tail.u32(end + 12)
            var directoryOffset = tail.u32(end + 16)
            if (count == U16_MAX || directorySize == U32_MAX || directoryOffset == U32_MAX) {
                val zip64 = zip64End(source, source.size - tailSize + end)
                count = zip64.getLong(32)
                directorySize = zip64.getLong(40)
                directoryOffset = zip64.getLong(48)
            }
            if (directorySize > Int.MAX_VALUE) throw IOException("The zip's table of contents is too big")
            val directory = buffer(directorySize.toInt())
            source.readFully(directoryOffset, directory)
            directory.flip()
            val entries = LinkedHashMap<String, Entry>()
            var at = 0
            repeat(count.toInt()) {
                if (directory.getInt(at) != CENTRAL_HEADER) throw IOException("Damaged zip")
                val nameLength = directory.u16(at + 28)
                val extraLength = directory.u16(at + 30)
                val commentLength = directory.u16(at + 32)
                val nameBytes = ByteArray(nameLength)
                directory.get(at + 46, nameBytes)
                var compressed = directory.u32(at + 20)
                var size = directory.u32(at + 24)
                var offset = directory.u32(at + 42)
                // zip64 keeps the real values in an extra field, in this order, for those set to all ones.
                var extra = at + 46 + nameLength
                val extraEnd = extra + extraLength
                while (extra + 4 <= extraEnd) {
                    val id = directory.u16(extra)
                    val length = directory.u16(extra + 2)
                    if (id == 0x0001) {
                        var field = extra + 4
                        if (size == U32_MAX) size = directory.getLong(field).also { field += 8 }
                        if (compressed == U32_MAX) compressed = directory.getLong(field).also { field += 8 }
                        if (offset == U32_MAX) offset = directory.getLong(field)
                    }
                    extra += 4 + length
                }
                val name = nameBytes.decodeToString()
                entries[name] = Entry(name, directory.u16(at + 10), compressed, size, offset)
                at = extraEnd + commentLength
            }
            return ZipIndex(source, entries)
        }

        private fun zip64End(source: RandomAccess, endOffset: Long): ByteBuffer {
            val locator = buffer(20)
            source.readFully(endOffset - 20, locator)
            locator.flip()
            if (locator.getInt(0) != ZIP64_LOCATOR) throw IOException("Damaged zip64")
            val end = buffer(56)
            source.readFully(locator.getLong(8), end)
            end.flip()
            if (end.getInt(0) != ZIP64_END) throw IOException("Damaged zip64")
            return end
        }

        private fun inflate(data: ByteArray, size: Int): ByteArray {
            val inflater = Inflater(true)
            try {
                inflater.setInput(data)
                val out = ByteArray(size)
                var at = 0
                while (at < size) {
                    val count = inflater.inflate(out, at, size - at)
                    if (count == 0 && (inflater.finished() || inflater.needsInput())) break
                    at += count
                }
                if (at != size) throw IOException("Damaged zip entry")
                return out
            } finally {
                inflater.end()
            }
        }

        private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        private fun ByteBuffer.u16(at: Int): Int = getShort(at).toInt() and 0xFFFF

        private fun ByteBuffer.u32(at: Int): Long = getInt(at).toLong() and U32_MAX
    }
}
