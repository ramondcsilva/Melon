/*
 * Copyright (c) 2018.
 * João Paulo Sena <joaopaulo761@gmail.com>
 *
 * This file is part of the UNES Open Source Project.
 *
 * UNES is licensed under the MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.forcetower.uefs.feature.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

fun Context.isNavBarOnBottom(): Boolean {
    val config = resources.configuration
    val dm = resources.displayMetrics
    val canMove = dm.widthPixels != dm.heightPixels && config.smallestScreenWidthDp < 600
    return !canMove || dm.widthPixels < dm.heightPixels
}

fun Context.openURL(url: String) {
    var fixed = url
    if (!url.startsWith("http://") &&
        !url.startsWith("HTTP://") &&
        !url.startsWith("HTTPS://") &&
        !url.startsWith("https://") &&
        !url.contains("//")
    ) {
        fixed = "http://$url"
    }

    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(fixed)
    this.startActivity(intent)
}

fun isNougatMR1(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
}