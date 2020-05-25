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

import android.app.Activity
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import com.docwei.simplegrafika.MoviePlayer.PlayTask
import com.docwei.simplegrafika.MoviePlayer.PlayerFeedback
import java.io.File

/**
 * Play a movie from a file on disk.  Output goes to a TextureView.
 *
 *
 * Currently video-only.
 *
 *
 * Contrast with PlayMovieSurfaceActivity, which uses a SurfaceView.  Much of the code is
 * the same, but here we can handle the aspect ratio adjustment with a simple matrix,
 * rather than a custom layout.
 *
 *
 * TODO: investigate crash when screen is rotated while movie is playing (need
 * to have onPause() wait for playback to stop)
 */
class PlayMovieActivity : Activity(), OnItemSelectedListener, SurfaceTextureListener,
    PlayerFeedback {
    private var mTextureView: TextureView? = null
    lateinit var mMovieFiles: Array<String>
    private var mSelectedMovie = 0
    private var mShowStopLabel = false
    private var mPlayTask: PlayTask? = null
    private var mSurfaceTextureReady = false
    private val mStopper = Any() // used to signal stop
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playingmovie)



        mTextureView = findViewById<View>(R.id.movie_texture_view) as TextureView
        mTextureView!!.surfaceTextureListener = this
        // Populate file-selection spinner.
        val spinner = findViewById<View>(R.id.playMovieFile_spinner) as Spinner
        // Need to create one of these fancy ArrayAdapter thingies, and specify the generic layout
        // for the widget itself.
        mMovieFiles = getFiles(filesDir, "*.mp4")
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item, mMovieFiles
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner.
        spinner.adapter = adapter
        spinner.onItemSelectedListener = this
        updateControls()
    }

    override fun onResume() {
        Log.d("grafika", "PlayMovieActivity onResume")
        super.onResume()
    }

    override fun onPause() {
        Log.d("grafika", "PlayMovieActivity onPause")
        super.onPause()
        // We're not keeping track of the state in static fields, so we need to shut the
// playback down.  Ideally we'd preserve the state so that the player would continue
// after a device rotation.
//
// We want to be sure that the player won't continue to send frames after we pause,
// because we're tearing the view down.  So we wait for it to stop here.
        if (mPlayTask != null) {
            stopPlayback()
            mPlayTask!!.waitForStop()
        }
    }

    override fun onSurfaceTextureAvailable(
        st: SurfaceTexture,
        width: Int,
        height: Int
    ) { // There's a short delay between the start of the activity and the initialization
// of the SurfaceTexture that backs the TextureView.  We don't want to try to
// send a video stream to the TextureView before it has initialized, so we disable
// the "play" button until this callback fires.
        Log.d("grafika", "SurfaceTexture ready (" + width + "x" + height + ")")
        mSurfaceTextureReady = true
        updateControls()
    }

    override fun onSurfaceTextureSizeChanged(
        st: SurfaceTexture,
        width: Int,
        height: Int
    ) { // ignore
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        mSurfaceTextureReady = false
        // assume activity is pausing, so don't need to update controls
        return true // caller should release ST
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { // ignore
    }

    /*
     * Called when the movie Spinner gets touched.
     */
    override fun onItemSelected(
        parent: AdapterView<*>,
        view: View,
        pos: Int,
        id: Long
    ) {
        val spinner = parent as Spinner
        mSelectedMovie = spinner.selectedItemPosition
        Log.d(
            "grafika",
            "onItemSelected: " + mSelectedMovie + " '" + mMovieFiles[mSelectedMovie] + "'"
        )
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}
    /**
     * onClick handler for "play"/"stop" button.
     */
    fun clickPlayStop(unused: View?) {
        if (mShowStopLabel) {
            Log.d("grafika", "stopping movie")
            stopPlayback()
            // Don't update the controls here -- let the task thread do it after the movie has
// actually stopped.
//mShowStopLabel = false;
//updateControls();
        } else {
            if (mPlayTask != null) {
                Log.w("grafika", "movie already playing")
                return
            }
            Log.d("grafika", "starting movie")
            val callback = SpeedControlCallback()
            if ((findViewById<View>(R.id.locked60fps_checkbox) as CheckBox).isChecked) { // TODO: consider changing this to be "free running" mode
                callback.setFixedPlaybackRate(60)
            }
            val st = mTextureView!!.surfaceTexture
            val surface = Surface(st)
            var player: MoviePlayer? = null
            player = try {
                MoviePlayer(
                    File(filesDir, mMovieFiles[mSelectedMovie]), surface, callback
                )
            } catch (ioe: Exception) {
                Log.e("grafika", "Unable to play movie", ioe)
                surface.release()
                return
            }
            adjustAspectRatio(player.videoWidth, player.videoHeight)
            mPlayTask = PlayTask(player, this)
            if ((findViewById<View>(R.id.loopPlayback_checkbox) as CheckBox).isChecked) {
                mPlayTask!!.loopMode = true
            }
            mShowStopLabel = true
            updateControls()
            mPlayTask!!.execute()
        }
    }

    /**
     * Requests stoppage if a movie is currently playing.  Does not wait for it to stop.
     */
    private fun stopPlayback() {
        if (mPlayTask != null) {
            mPlayTask!!.requestStop()
        }
    }

    // MoviePlayer.PlayerFeedback
    override fun playbackStopped() {
        Log.d("grafika", "playback stopped")
        mShowStopLabel = false
        mPlayTask = null
        updateControls()
    }

    /**
     * Sets the TextureView transform to preserve the aspect ratio of the video.
     */
    private fun adjustAspectRatio(videoWidth: Int, videoHeight: Int) {
        val viewWidth = mTextureView!!.width
        val viewHeight = mTextureView!!.height
        val aspectRatio = videoHeight.toDouble() / videoWidth
        val newWidth: Int
        val newHeight: Int
        if (viewHeight > (viewWidth * aspectRatio).toInt()) { // limited by narrow width; restrict height
            newWidth = viewWidth
            newHeight = (viewWidth * aspectRatio).toInt()
        } else { // limited by short height; restrict width
            newWidth = (viewHeight / aspectRatio).toInt()
            newHeight = viewHeight
        }
        val xoff = (viewWidth - newWidth) / 2
        val yoff = (viewHeight - newHeight) / 2
        Log.v(
            "grafika", "video=" + videoWidth + "x" + videoHeight +
                    " view=" + viewWidth + "x" + viewHeight +
                    " newView=" + newWidth + "x" + newHeight +
                    " off=" + xoff + "," + yoff
        )
        val txform = Matrix()
        mTextureView!!.getTransform(txform)
        txform.setScale(
            newWidth.toFloat() / viewWidth,
            newHeight.toFloat() / viewHeight
        )
        //txform.postRotate(10);          // just for fun
        txform.postTranslate(xoff.toFloat(), yoff.toFloat())
        mTextureView!!.setTransform(txform)
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private fun updateControls() {
        val play =
            findViewById<View>(R.id.play_stop_button) as Button
        if (mShowStopLabel) {
            play.text = "stop"
        } else {
            play.text = "play"
        }
        play.isEnabled = mSurfaceTextureReady
        // We don't support changes mid-play, so dim these.
        var check =
            findViewById<View>(R.id.locked60fps_checkbox) as CheckBox
        check.isEnabled = !mShowStopLabel
        check =
            findViewById<View>(R.id.loopPlayback_checkbox) as CheckBox
        check.isEnabled = !mShowStopLabel
    }
}