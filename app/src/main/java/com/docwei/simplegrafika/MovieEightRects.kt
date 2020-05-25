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
 * Generates a very simple movie.  The screen is divided into eight rectangles, and one
 * rectangle is highlighted in each frame.
 *
 *
 * To add a little flavor, the timing of the frames speeds up as the movie continues.
 */
class MovieEightRects : GeneratedMovie() {
    override fun create(outputFile: File?, prog: ProgressUpdater?) {
        if (mMovieReady) {
            throw RuntimeException("Already created")
        }
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
     * Generates a frame of data using GL commands.  We have an 8-frame animation
     * sequence that wraps around.  It looks like this:
     * <pre>
     * 0 1 2 3
     * 7 6 5 4
    </pre> *
     * We draw one of the eight rectangles and leave the rest set to the clear color.
     */
    private fun generateFrame(frameIndex: Int) {
        var frameIndex = frameIndex
        frameIndex %= 8
        val startX: Int
        val startY: Int
        if (frameIndex < 4) { // (0,0) is bottom-left in GL
            startX = frameIndex * (WIDTH / 4)
            startY = HEIGHT / 2
        } else {
            startX = (7 - frameIndex) * (WIDTH / 4)
            startY = 0
        }
        GLES20.glClearColor(
            TEST_R0 / 255.0f,
            TEST_G0 / 255.0f,
            TEST_B0 / 255.0f,
            1.0f
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            startX,
            startY,
            WIDTH / 4,
            HEIGHT / 2
        )
        GLES20.glClearColor(
            TEST_R1 / 255.0f,
            TEST_G1 / 255.0f,
            TEST_B1 / 255.0f,
            1.0f
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    companion object {
        private const val MIME_TYPE = "video/avc"
        private const val WIDTH = 320
        private const val HEIGHT = 240
        private const val BIT_RATE = 2000000
        private const val NUM_FRAMES = 32
        private const val FRAMES_PER_SECOND = 30
        // RGB color values for generated frames
        private const val TEST_R0 = 0
        private const val TEST_G0 = 136
        private const val TEST_B0 = 0
        private const val TEST_R1 = 236
        private const val TEST_G1 = 50
        private const val TEST_B1 = 186
        /**
         * Generates the presentation time for frame N, in nanoseconds.
         *
         *
         * First 8 frames at 8 fps, next 8 at 16fps, rest at 30fps.
         */
        private fun computePresentationTimeNsec(frameIndex: Int): Long {
            var frameIndex = frameIndex
            val ONE_BILLION: Long = 1000000000
            var time: Long
            if (frameIndex < 8) { // 8 fps
                return frameIndex * ONE_BILLION / 8
            } else {
                time = ONE_BILLION
                frameIndex -= 8
            }
            if (frameIndex < 8) {
                return time + frameIndex * ONE_BILLION / 16
            } else {
                time += ONE_BILLION / 2
                frameIndex -= 8
            }
            return time + frameIndex * ONE_BILLION / 30
        }
    }
}