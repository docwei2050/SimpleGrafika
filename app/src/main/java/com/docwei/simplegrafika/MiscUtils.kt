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
@file:JvmName("MiscUtils")
package com.docwei.simplegrafika

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import java.io.File
import java.util.*
import java.util.regex.Pattern

/**
 * Some handy utilities.
 */

    /**
     * Obtains a list of files that live in the specified directory and match the glob pattern.
     */
    fun getFiles(dir: File, glob: String): Array<String> {
        val regex = globToRegex(glob)
        val pattern = Pattern.compile(regex)
        val result = dir.list { dir, name ->
            val matcher = pattern.matcher(name)
            matcher.matches()
        }
        Arrays.sort(result)
        return result
    }

    /**
     * Converts a filename globbing pattern to a regular expression.
     *
     *
     * The regex is suitable for use by Matcher.matches(), which matches the entire string, so
     * we don't specify leading '^' or trailing '$'.
     */
    private fun globToRegex(glob: String): String { // Quick, overly-simplistic implementation -- just want to handle something simple
// like "*.mp4".
//
// See e.g. http://stackoverflow.com/questions/1247772/ for a more thorough treatment.
        val regex = StringBuilder(glob.length)
        //regex.append('^');
        for (ch in glob.toCharArray()) {
            when (ch) {
                '*' -> regex.append(".*")
                '?' -> regex.append('.')
                '.' -> regex.append("\\.")
                else -> regex.append(ch)
            }
        }
        //regex.append('$');
        return regex.toString()
    }

    /**
     * Obtains the approximate refresh time, in nanoseconds, of the default display associated
     * with the activity.
     *
     *
     * The actual refresh rate can vary slightly (e.g. 58-62fps on a 60fps device).
     */
    fun getDisplayRefreshNsec(activity: Activity): Long {
        val display =
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val displayFps = display.refreshRate.toDouble()
        return Math.round(1000000000L / displayFps)
    }
