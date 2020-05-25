package com.docwei.simplegrafika

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.RuntimeException

class MoviePlayer constructor(val sourceFile: File, val outputSurface: Surface, val frameCallback: FrameCallback) {
    val mBufferInfo by lazy {
        MediaCodec.BufferInfo()
    }
    @Volatile
    var mIsStopRequested: Boolean = false
    var mLoopMode: Boolean = false;
    var videoWidth: Int = 0;
    var videoHeight = 0;
    init {
        var extractor: MediaExtractor ?=null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(sourceFile.toString())
            val trackIndex = selectTrack(extractor)
            if (trackIndex < 0) {
                throw RuntimeException("No video track found in " + sourceFile)
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            videoWidth = format.getInteger(MediaFormat.KEY_WIDTH)
            videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
        } finally {
            extractor?.release()
            extractor = null
        }

    }

    fun requestStop() {
        mIsStopRequested = true
    }

    @Throws(IOException::class)
    fun play() {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null;
        if (!sourceFile.canRead()) {
            throw  FileNotFoundException("unable to read " + sourceFile)
        }
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(sourceFile.toString())
            var trackIndex = selectTrack(extractor)
            if (trackIndex < 0) {
                throw RuntimeException("No video track found in " + sourceFile)
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mine = format.getString(MediaFormat.KEY_MIME)
            val decoder = MediaCodec.createDecoderByType(mine)
            decoder.configure(format, outputSurface, null, 0)
            decoder.start()
            doEXtract(extractor, trackIndex, decoder, frameCallback)

        } finally {
            decoder?.stop()
            decoder?.release()
            decoder = null
            extractor?.release()
            extractor = null
        }
    }

    fun doEXtract(
        extractor: MediaExtractor,
        trackIndex: Int,
        decoder: MediaCodec,
        frameCallback: FrameCallback
    ) {
        val TIMEOUT_USEC = 10_000L;
        val decoderInputBuffers = decoder.getInputBuffers();
        var inputChunk = 0;
        var firstInputTimeNsec = -1L;
        var outputDone = false
        var inputDone = false
        while (!outputDone) {
            Log.e("grafika", "loop")
            if (mIsStopRequested) {
                Log.e("grafika", "stop requested")
                return
            }
            if (!inputDone) {
                val inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (firstInputTimeNsec == -1L) {
                        firstInputTimeNsec = System.nanoTime()
                    }
                    val inputBuf = decoderInputBuffers[inputBufIndex];
                    val chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true;
                        Log.e("grafika", "sent intput EOS")
                    } else if (extractor.getSampleTrackIndex() != trackIndex) {
                        Log.e(
                            "grafika",
                            "get sample from track ${extractor.sampleTrackIndex},expected ${trackIndex}"
                        )
                    }
                    val presentationTimeUs = extractor.sampleTime;
                    decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, presentationTimeUs, 0)
                    Log.e("grafika", "submitted frame ${inputChunk} to dec,size= ${chunkSize}")
                    inputChunk++;
                    extractor.advance()
                } else {
                    Log.e("grafika", "input buffer not available")
                }
            }
            if (!outputDone) {
                val decodeStatus = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (decodeStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e("grafika", "no output from decoder available")
                } else if (decodeStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.e("grafika", "decoder output buffers changed")
                } else if (decodeStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    var newFormat = decoder.getOutputFormat()
                    Log.e("grafika", "decoder output format changed")
                } else if (decodeStatus < 0) {
                    throw RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decodeStatus)
                } else {
                    if (firstInputTimeNsec != 0L) {
                        val nowNsec = System.nanoTime()
                        Log.e(
                            "grafika",
                            "startup lag ${((nowNsec - firstInputTimeNsec) / 1000_000.0)} ms"
                        )
                        firstInputTimeNsec = 0
                    }
                    var doLoop = false;
                    Log.e(
                        "grafika",
                        "suface decoder given buffer ${decodeStatus} size= ${mBufferInfo.size}"
                    )
                    if ((mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.e("grafika", "output EOS");
                        if (mLoopMode) {
                            doLoop = true
                        } else {
                            outputDone = true
                        }
                    }
                    val doRender = mBufferInfo.size != 0
                    if (doRender && frameCallback != null) {
                        frameCallback.preRender(mBufferInfo.presentationTimeUs)
                    }
                    decoder.releaseOutputBuffer(decodeStatus, doRender)
                    if (doRender && frameCallback != null) {
                        frameCallback.postRender();
                    }
                    if (doLoop) {
                        Log.e("grafika", "Reached EOS,looping")
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        inputDone = false
                        decoder.flush()//reset decoder state
                        frameCallback.loopReset()
                    }
                }
            }

        }
    }

    class PlayTask constructor(var player: MoviePlayer, var feedBack: PlayerFeedback) : Runnable {
        var loopMode = false
        lateinit var thread: Thread
        var localHandler: LocalHandler
        var stopLock = Object()
        var stopped = false

        init {
            localHandler = LocalHandler()
        }

        override fun run() {
            try {
                player.play()
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            } finally {
                synchronized(stopLock) {
                    stopped = true
                    stopLock.notifyAll()
                }
                localHandler.sendMessage(localHandler.obtainMessage(MSG_PLAY_STOPPED, feedBack))
            }
        }

        fun execute() {
            player.mLoopMode = loopMode
            thread = Thread(this, "movie player")
            thread.start()
        }

        fun requestStop() {
            player.requestStop()
        }

        fun waitForStop() {
            synchronized(stopLock) {
                while (!stopped) {
                    try {
                        stopLock.wait()
                    } catch (e: InterruptedException) {
                        //discard
                    }
                }
            }
        }


    }

    class LocalHandler : Handler() {
        override fun handleMessage(msg: Message) {
            var what = msg.what
            when (what) {
                MSG_PLAY_STOPPED -> {
                    var fb = msg.obj as PlayerFeedback;
                    fb.playbackStopped()
                }
                else -> {
                    throw RuntimeException("Unknow msg ${what}")
                }
            }
        }
    }

    interface PlayerFeedback {
        fun playbackStopped()
    }

    interface FrameCallback {
        fun preRender(presentationTimeUsec: Long)
        fun postRender()
        fun loopReset();
    }
}


val MSG_PLAY_STOPPED = 0
fun selectTrack(extractor: MediaExtractor): Int {
    val numTracks = extractor.getTrackCount();
    for (i in 0 until numTracks) {
        val format = extractor.getTrackFormat(i);
        val mine = format.getString(MediaFormat.KEY_MIME);
        if (mine.startsWith("video/")) {
            Log.e("grafika", "extractor selected track ${i} ${mine}  ${format}")
            return i;
        }
    }
    return -1;
}