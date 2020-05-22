package com.docwei.simplegrafika

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Choreographer
import android.view.Surface
import java.io.File
import java.lang.RuntimeException

class MoviePlayer constructor(
    val sourceFile: File,
    val ouotputSurface: Surface,
    val frameCallback: FrameCallback
) {
    val mBufferInfo by lazy {
        MediaCodec.BufferInfo()
    }
    @Volatile
    var mIsStopRequested: Boolean = false
    var mLoop: Boolean = false;
    //默认自带setter getter方法
    var mVideoWidth: Int=0;
    var mVideoHeigth = 0;

    init {
        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(sourceFile.toString())
            var trackIndex = selectTrack(extractor)
            if (trackIndex < 0) {
                throw RuntimeException("No video track found in " + sourceFile)
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH)
            mVideoHeigth = format.getInteger(MediaFormat.KEY_HEIGHT)
        } finally {
            extractor?.release()
            extractor = null
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