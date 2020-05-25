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

import android.opengl.GLES20
import android.util.Log
import com.docwei.simplegrafika.ContentManager.ProgressUpdater
import java.io.File
import java.io.IOException

/**
 * Generates a simple movie, featuring two small rectangles that slide across the screen.
 */
class MovieSliders : GeneratedMovie() {
    override fun create(outputFile: File?, prog: ProgressUpdater?) {
        if (mMovieReady) {
            throw RuntimeException("Already created")
        }
        val NUM_FRAMES = 240
        try {
            prepareEncoder(
                MIME_TYPE,
                WIDTH,
                HEIGHT,
                BIT_RATE,
                FRAMES_PER_SECOND,
                outputFile!!
            )
            for (i in 0 until NUM_FRAMES) { // Drain any data from the encoder into the muxer.
                drainEncoder(false)
                // Generate a frame and submit it.
                generateFrame(i)
                submitFrame(computePresentationTimeNsec(i))
                prog!!.updateProgress(i * 100 / NUM_FRAMES)
            }
            // Send end-of-stream and drain remaining output.
            drainEncoder(true)
        } catch (ioe: IOException) {
            throw RuntimeException(ioe)
        } finally {
            releaseEncoder()
        }
        Log.d("grafika", "MovieEightRects complete: $outputFile")
        mMovieReady = true
    }

    /**
     * Generates a frame of data using GL commands.
     */
    private fun generateFrame(frameIndex: Int) {
        var frameIndex = frameIndex
        val BOX_SIZE = 80
        frameIndex %= 240
        val xpos: Int
        val ypos: Int
        val absIndex = Math.abs(frameIndex - 120)
        xpos = absIndex * WIDTH / 120
        ypos = absIndex * HEIGHT / 120
        val lumaf = absIndex / 120.0f
        GLES20.glClearColor(lumaf, lumaf, lumaf, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(BOX_SIZE / 2, ypos, BOX_SIZE, BOX_SIZE)
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glScissor(xpos, BOX_SIZE / 2, BOX_SIZE, BOX_SIZE)
        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    companion object {
        private const val MIME_TYPE = "video/avc"
        private const val WIDTH = 480 // note 480x640, not 640x480
        private const val HEIGHT = 640
        private const val BIT_RATE = 5000000
        private const val FRAMES_PER_SECOND = 30
        /**
         * Generates the presentation time for frame N, in nanoseconds.  Fixed frame rate.
         */
        private fun computePresentationTimeNsec(frameIndex: Int): Long {
            val ONE_BILLION: Long = 1000000000
            return frameIndex * ONE_BILLION / FRAMES_PER_SECOND
        }
    }
}