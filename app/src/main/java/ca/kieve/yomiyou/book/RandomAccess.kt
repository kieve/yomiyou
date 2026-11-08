package ca.kieve.yomiyou.book

import java.io.Closeable
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Bytes read at any position: a file through its channel, or bytes already in memory. */
interface RandomAccess : Closeable {
    val size: Long

    /** Fills [into] from [position] on. */
    fun readFully(position: Long, into: ByteBuffer)
}

class ChannelAccess(private val channel: FileChannel, private val onClose: () -> Unit = {}) : RandomAccess {
    override val size: Long get() = channel.size()

    override fun readFully(position: Long, into: ByteBuffer) {
        var at = position
        while (into.hasRemaining()) {
            val read = channel.read(into, at)
            if (read < 0) throw EOFException("Read past the end at $at")
            at += read
        }
    }

    override fun close() {
        channel.close()
        onClose()
    }
}

class BytesAccess(private val bytes: ByteArray) : RandomAccess {
    override val size: Long get() = bytes.size.toLong()

    override fun readFully(position: Long, into: ByteBuffer) {
        val count = into.remaining()
        if (position < 0 || position + count > bytes.size) throw EOFException("Read past the end at $position")
        into.put(bytes, position.toInt(), count)
    }

    override fun close() {}
}
