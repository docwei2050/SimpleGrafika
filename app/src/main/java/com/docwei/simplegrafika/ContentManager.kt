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
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.AsyncTask
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.util.*

class ContentManager private constructor() {
    private var mInitialized = false
    private var mFilesDir: File? = null
    private var mContent: ArrayList<Content>? = null

    fun isContentCreated(unused: Context?): Boolean {
        for (i in ALL_TAGS.indices) {
            val file = getPath(i)
            if (!file.canRead()) {
                Log.d("TAG", "Can't find readable $file")
                return false
            }
        }
        return true
    }

    fun createAll(caller: Activity) {
        prepareContent(caller, ALL_TAGS)
    }

    fun prepareContent(
        caller: Activity,
        tags: IntArray
    ) { // Put up the progress dialog.
        val builder: AlertDialog.Builder =
            WorkDialog.create(caller, R.string.preparing_content)
        builder.setCancelable(false)
        val dialog = builder.show()
        // Generate content in async task.
        val genTask = GenerateTask(caller, dialog, tags)
        genTask.execute()
    }

    /**
     * Returns the specified item.
     */
    fun getContent(tag: Int): Content {
        synchronized(mContent!!) { return mContent!![tag] }
    }

    /**
     * Prepares the specified item.
     *
     *
     * This may be called from the async task thread.
     */
    private fun prepare(prog: ProgressUpdater, tag: Int) {
        val movie: GeneratedMovie
        when (tag) {
            MOVIE_EIGHT_RECTS -> {
                movie = MovieEightRects()
                movie.create(getPath(tag), prog)
                synchronized(mContent!!) { mContent!!.add(tag, movie) }
            }
            MOVIE_SLIDERS -> {
                movie = MovieSliders()
                movie.create(getPath(tag), prog)
                synchronized(mContent!!) { mContent!!.add(tag, movie) }
            }
            else -> throw RuntimeException("Unknown tag $tag")
        }
    }

    /**
     * Returns the filename for the tag.
     */
    private fun getFileName(tag: Int): String {
        return when (tag) {
            MOVIE_EIGHT_RECTS -> "gen-eight-rects.mp4"
            MOVIE_SLIDERS -> "gen-sliders.mp4"
            else -> throw RuntimeException("Unknown tag $tag")
        }
    }

    /**
     * Returns the storage location for the specified item.
     */
    fun getPath(tag: Int): File {
        return File(mFilesDir, getFileName(tag))
    }

    interface ProgressUpdater {
        /**
         * Updates a progress meter.
         * @param percent Percent completed (0-100).
         */
        fun updateProgress(percent: Int)
    }

    /**
     * Performs generation of content on an async task thread.
     */
   class GenerateTask(private val mContext: Context, private val mPrepDialog: AlertDialog,
        // ----- accessed from both -----
        private val mTags: IntArray
    ) : AsyncTask<Void?, Int?, Int>(), ProgressUpdater {
        private val mProgressBar: ProgressBar
        // ----- accessed from async thread -----
        private var mCurrentIndex = 0
        @Volatile
        private var mFailure: RuntimeException? = null

        // async task thread
        override  fun doInBackground(vararg params: Void?): Int {
            val contentManager = instance
            Log.d("TAG", "doInBackground...")
            for (i in mTags.indices) {
                mCurrentIndex = i
                updateProgress(0)
                try {
                    contentManager!!.prepare(this, mTags[i])
                } catch (re: RuntimeException) {
                    mFailure = re
                    break
                }
                updateProgress(100)
            }
            if (mFailure != null) {
                Log.w("TAG",
                    "Failed while generating content",
                    mFailure
                )
            } else {
                Log.d("TAG", "generation complete")
            }
            return 0
        }

        // async task thread
        override fun updateProgress(percent: Int) {
            publishProgress(mCurrentIndex, percent)
        }

        // UI thread
         fun onProgressUpdate(vararg progressArray: Int) {
            val index = progressArray[0]
            val percent = progressArray[1]
            //Log.d(TAG, "progress " + index + "/" + percent + " of " + mTags.length * 100);
            if (percent == 0) {
                val name =
                    mPrepDialog.findViewById<View>(R.id.workJobName_text) as TextView
                name.text = instance!!.getFileName(mTags[index])
            }
            mProgressBar.progress = index * 100 + percent
        }

        // UI thread
        override fun onPostExecute(result: Int) {
            mPrepDialog.dismiss()
            if (mFailure != null) {
                showFailureDialog(mContext, mFailure!!)
            }
        }

        /**
         * Posts an error dialog, including the message from the failure exception.
         */
        private fun showFailureDialog(context: Context, failure: RuntimeException) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Unable to generate content")
            val msg = "Failed to generate content.  Some features may be unavailable"
            builder.setMessage(msg)
            builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, id -> dialog.dismiss() })
            builder.setCancelable(false)
            val dialog = builder.create()
            dialog.show()
        }

        init {
            mProgressBar =
                mPrepDialog.findViewById<View>(R.id.work_progress) as ProgressBar
            mProgressBar.max = mTags.size * 100
        }


    }

    companion object {
        const val MOVIE_EIGHT_RECTS = 0
        const val MOVIE_SLIDERS = 1
        private val ALL_TAGS = intArrayOf(
            MOVIE_EIGHT_RECTS,
            MOVIE_SLIDERS
        )
        // Housekeeping.
        private val sLock = Any()
        private var sInstance: ContentManager? = null
        /**
         * Returns the singleton instance.
         */
        val instance: ContentManager?
            get() {
                synchronized(sLock) {
                    if (sInstance == null) {
                        sInstance = ContentManager()
                    }
                    return sInstance
                }
            }

        fun initialize(context: Context) {
            val mgr = instance
            synchronized(sLock) {
                if (!mgr!!.mInitialized) {
                    mgr.mFilesDir = context.filesDir
                    mgr.mContent = ArrayList()
                    mgr.mInitialized = true
                }
            }
        }
    }
}