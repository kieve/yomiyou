package ca.kieve.yomiyou.narration

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.nio.ByteBuffer

/** A segment of the voice file being narrated, by `yomiyou-voice://<book id>/<voice id>/<segment>`, read in place. */
@OptIn(UnstableApi::class)
class VoiceDataSource(private val narration: Narration) : BaseDataSource(false) {
    class Factory(private val narration: Narration) : DataSource.Factory {
        override fun createDataSource(): DataSource = VoiceDataSource(narration)
    }

    private var uri: Uri? = null
    private var file: VoiceFile? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val (voice, range) = narration.audio(dataSpec.uri) ?: throw IOException("No audio at ${dataSpec.uri}")
        val size = range.last - range.first + 1
        if (dataSpec.position > size) throw IOException("Read past the end of ${dataSpec.uri}")
        position = range.first + dataSpec.position
        remaining = size - dataSpec.position
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) remaining = minOf(remaining, dataSpec.length)
        uri = dataSpec.uri
        file = voice
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = minOf(length.toLong(), remaining).toInt()
        val voice = file ?: throw IOException("Not open")
        voice.readFully(position, ByteBuffer.wrap(buffer, offset, count))
        position += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (uri != null) {
            uri = null
            file = null
            transferEnded()
        }
    }

    companion object {
        const val SCHEME = "yomiyou-voice"

        fun uri(bookId: String, voice: String, segment: Int): Uri =
            Uri.Builder().scheme(SCHEME).authority(bookId).appendPath(voice).appendPath(segment.toString()).build()
    }
}
