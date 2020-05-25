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
import android.view.InflateException
import android.view.View

/**
 * Utility functions for work_dialog.
 */
object WorkDialog {
    /**
     * Prepares an alert dialog builder, using the work_dialog view.
     *
     *
     * The caller should finish populating the builder, then call AlertDialog.Builder#show().
     */
    fun create(activity: Activity, titleId: Int): AlertDialog.Builder {
        val view: View
        view = try {
            activity.layoutInflater.inflate(R.layout.work_dialog, null)
        } catch (ie: InflateException) {
            throw ie
        }
        val title = activity.getString(titleId)
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(title)
        builder.setView(view)
        return builder
    }
}