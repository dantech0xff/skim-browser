package com.dantech.skimbrowser.browser.downloads

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

data class ElementarySample(val data: ByteArray, val timeUs: Long, val flags: Int)

interface OpenedTrack {
    val mime: String
    fun read(): ElementarySample?
}

interface ElementarySource : AutoCloseable {
    fun tracks(): List<OpenedTrack>
}

interface MuxSink : AutoCloseable {
    fun addTrack(mime: String): Int
    fun start()
    fun write(track: Int, sample: ElementarySample)
    fun stop()
}

/**
 * Writes the first video track, then the first audio track. The file muxer does the same copy
 * with the track formats [MediaExtractor] reports.
 */
fun muxSources(video: ElementarySource, audio: ElementarySource, sink: MuxSink) {
    video.use { videoSource ->
        audio.use { audioSource ->
            val videoTrack = videoSource.tracks().firstOrNull { it.mime.startsWith("video/") }
                ?: error("The video file has no video track")
            val audioTrack = audioSource.tracks().firstOrNull { it.mime.startsWith("audio/") }
                ?: error("The audio file has no audio track")
            val videoIndex = sink.addTrack(videoTrack.mime)
            val audioIndex = sink.addTrack(audioTrack.mime)
            sink.start()
            writeAll(sink, videoIndex, videoTrack)
            writeAll(sink, audioIndex, audioTrack)
            sink.stop()
        }
    }
}

fun muxToMp4(video: File, audio: File, out: File) {
    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    try {
        videoExtractor.setDataSource(video.absolutePath)
        audioExtractor.setDataSource(audio.absolutePath)
        val videoIndex = trackIndex(videoExtractor, "video/")
        val audioIndex = trackIndex(audioExtractor, "audio/")
        val videoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoIndex))
        val audioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioIndex))
        videoExtractor.selectTrack(videoIndex)
        audioExtractor.selectTrack(audioIndex)
        muxer.start()
        copySamples(videoExtractor, muxer, videoTrack)
        copySamples(audioExtractor, muxer, audioTrack)
        muxer.stop()
    } finally {
        videoExtractor.release()
        audioExtractor.release()
        muxer.release()
    }
}

private fun writeAll(sink: MuxSink, index: Int, track: OpenedTrack) {
    while (true) {
        sink.write(index, track.read() ?: return)
    }
}

private fun trackIndex(extractor: MediaExtractor, prefix: String): Int {
    for (index in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith(prefix)) return index
    }
    error("No $prefix track")
}

private fun copySamples(extractor: MediaExtractor, muxer: MediaMuxer, track: Int) {
    val buffer = ByteBuffer.allocate(1024 * 1024)
    val info = MediaCodec.BufferInfo()
    while (true) {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return
        info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
        muxer.writeSampleData(track, buffer, info)
        extractor.advance()
    }
}
