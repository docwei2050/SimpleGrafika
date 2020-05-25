/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.docwei.simplegrafika

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import com.docwei.simplegrafika.ContentManager.ProgressUpdater
import com.docwei.simplegrafika.gles.EglCore
import com.docwei.simplegrafika.gles.WindowSurface
import java.io.File
import java.io.IOException

/**
 * Base class for generated movies.
 */
abstract class GeneratedMovie : Content {
    // set by sub-class to indicate that the movie has been generated
// TODO: remove this now?
    protected var mMovieReady = false
    // "live" state during recording
    private var mBufferInfo: MediaCodec.BufferInfo? = null
    private var mEncoder: MediaCodec? = null
    private var mMuxer: MediaMuxer? = null
    private var mEglCore: EglCore? = null
    private var mInputSurface: WindowSurface? = null
    private var mTrackIndex = 0
    private var mMuxerStarted = false
    /**
     * Creates the movie content.  Usually called from an async task thread.
     */
    abstract fun create(outputFile: File?, prog: ProgressUpdater?)

    /**
     * Prepares the video encoder, muxer, and an EGL input surface.
     */
    @Throws(IOException::class)
    protected fun prepareEncoder(
        mimeType: String?, width: Int, height: Int, bitRate: Int,
        framesPerSecond: Int, outputFile: File
    ) {
        mBufferInfo = MediaCodec.BufferInfo()
        val format = MediaFormat.createVideoFormat(mimeType, width, height)
        // Set some properties.  Failing to specify some of these can cause the MediaCodec
// configure() call to throw an unhelpful exception.
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond)
        format.setInteger(
            MediaFormat.KEY_I_FRAME_INTERVAL,
            IFRAME_INTERVAL
        )
        if (VERBOSE) Log.d("grafika", "format: $format")
        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
// we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(mimeType!!)
        mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        Log.v("grafika", "encoder is " + mEncoder!!.codecInfo.name)
        val surface: Surface
        surface = try {
            mEncoder!!.createInputSurface()
        } catch (ise: IllegalStateException) { // This is generally the first time we ever try to encode something through a
// Surface, so specialize the message a bit if we can guess at why it's failing.
// TODO: failure message should come out of strings.xml for i18n
            if (isSoftwareCodec(mEncoder!!)) {
                throw RuntimeException(
                    "Can't use input surface with software codec: " +
                            mEncoder!!.codecInfo.name,
                    ise
                )
            } else {
                throw RuntimeException("Failed to create input surface", ise)
            }
        }
        mEglCore = EglCore(null, EglCore.FLAG_RECORDABLE)
        mInputSurface = WindowSurface(mEglCore!!, surface, true)
        mInputSurface?.makeCurrent()
        mEncoder!!.start()
        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
// because our MediaFormat doesn't have the Magic Goodies.  These can only be
// obtained from the encoder after it has started processing data.
//
// We're not actually interested in multiplexing audio.  We just want to convert
// the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        if (VERBOSE) Log.d(
            "grafika",
            "output will go to $outputFile"
        )
        mMuxer = MediaMuxer(
            outputFile.toString(),
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        mTrackIndex = -1
        mMuxerStarted = false
    }

    /**
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    protected fun releaseEncoder() {
        if (VERBOSE) Log.d(
            "grafika",
            "releasing encoder objects"
        )
        if (mEncoder != null) {
            mEncoder!!.stop()
            mEncoder!!.release()
            mEncoder = null
        }
        mInputSurface?.release()
        mInputSurface = null
        mEglCore?.release()
        mEglCore = null
        if (mMuxer != null) {
            mMuxer!!.stop()
            mMuxer!!.release()
            mMuxer = null
        }
    }

    /**
     * Submits a frame to the encoder.
     *
     * @param presentationTimeNsec The presentation time stamp, in nanoseconds.
     */
    protected fun submitFrame(presentationTimeNsec: Long) { // The eglSwapBuffers call will block if the input is full, which would be bad if
// it stayed full until we dequeued an output buffer (which we can't do, since we're
// stuck here).  So long as the caller fully drains the encoder before supplying
// additional input, the system guarantees that we can supply another frame
// without blocking.
        mInputSurface?.setPresentationTime(presentationTimeNsec)
        mInputSurface?.swapBuffers()
    }

    /**
     * Extracts all pending data from the encoder.
     *
     *
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     */
    protected fun drainEncoder(endOfStream: Boolean) {
        val TIMEOUT_USEC = 10000
        if (VERBOSE) Log.d(
            "grafika",
            "drainEncoder($endOfStream)"
        )
        if (endOfStream) {
            if (VERBOSE) Log.d(
                "grafika",
                "sending EOS to encoder"
            )
            mEncoder!!.signalEndOfInputStream()
        }
        var encoderOutputBuffers = mEncoder!!.outputBuffers
        while (true) {
            val encoderStatus =
                mEncoder!!.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) { // no output available yet
                if (!endOfStream) {
                    break // out of while
                } else {
                    if (VERBOSE) Log.d(
                        "grafika",
                        "no output available, spinning to await EOS"
                    )
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) { // not expected for an encoder
                encoderOutputBuffers = mEncoder!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = mEncoder!!.outputFormat
                Log.d("grafika", "encoder output format changed: $newFormat")
                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer!!.addTrack(newFormat)
                mMuxer!!.start()
                mMuxerStarted = true
            } else if (encoderStatus < 0) {
                Log.w(
                    "grafika", "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus
                )
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                    ?: throw RuntimeException(
                        "encoderOutputBuffer " + encoderStatus +
                                " was null"
                    )
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) { // The codec config data was pulled out and fed to the muxer when we got
// the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(
                        "grafika",
                        "ignoring BUFFER_FLAG_CODEC_CONFIG"
                    )
                    mBufferInfo!!.size = 0
                }
                if (mBufferInfo!!.size != 0) {
                    if (!mMuxerStarted) {
                        throw RuntimeException("muxer hasn't started")
                    }
                    // adjust the ByteBuffer values to match BufferInfo
                    encodedData.position(mBufferInfo!!.offset)
                    encodedData.limit(mBufferInfo!!.offset + mBufferInfo!!.size)
                    mMuxer!!.writeSampleData(mTrackIndex, encodedData, mBufferInfo!!)
                    if (VERBOSE) Log.d(
                        "grafika",
                        "sent " + mBufferInfo!!.size + " bytes to muxer"
                    )
                }
                mEncoder!!.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        Log.w("grafika", "reached end of stream unexpectedly")
                    } else {
                        if (VERBOSE) Log.d(
                            "grafika",
                            "end of stream reached"
                        )
                    }
                    break // out of while
                }
            }
        }
    }

    companion object {
        private const val VERBOSE = false
        private const val IFRAME_INTERVAL = 5
        /**
         * Returns true if the codec has a software implementation.
         */
        private fun isSoftwareCodec(codec: MediaCodec): Boolean {
            val codecName = codec.codecInfo.name
            return "OMX.google.h264.encoder" == codecName
        }
    }
}