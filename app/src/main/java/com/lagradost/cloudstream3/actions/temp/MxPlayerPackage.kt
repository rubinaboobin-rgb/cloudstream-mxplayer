package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.makeTempM3U8Intent
import com.lagradost.cloudstream3.actions.updateDurationAndPosition
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos

// MX Player API documentation: https://sites.google.com/site/maboroshin/mx-player-api
// Package names:
// - Free version: com.mxtech.videoplayer.ad
// - Pro version: com.mxtech.videoplayer.pro
//
// NOTE: This file can be extracted into a standalone CloudStream extension.
// To convert to extension:
// 1. Fork recloudstream/TestPlugins
// 2. Create MxPlayerPlugin.kt with:
//    @CloudstreamPlugin
//    class MxPlayerPlugin : Plugin() {
//        override fun load(context: Context) {
//            registerVideoClickAction(MxPlayerPackage())
//            registerVideoClickAction(MxPlayerProPackage())
//        }
//    }
// 3. Copy this file and adjust package name
// 4. Build with: .\gradlew MxPlayerPlugin:make

/** MX Player Pro variant */
class MxPlayerProPackage : MxPlayerPackage(
    appName = "MX Player Pro",
    packageName = "com.mxtech.videoplayer.pro",
    intentClass = "com.mxtech.videoplayer.ActivityScreen"
)

/** MX Player Free variant (with ads) */
open class MxPlayerPackage(
    appName: String = "MX Player",
    packageName: String = "com.mxtech.videoplayer.ad",
    intentClass: String = "com.mxtech.videoplayer.ad.ActivityScreen"
) : OpenInAppAction(
    appName = txt(appName),
    packageName = packageName,
    intentClass = intentClass,
    action = Intent.ACTION_VIEW
) {
    override val oneSource = true

    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        if (index != null) {
            intent.setDataAndType(result.links[index].url.toUri(), "video/*")
        } else {
            makeTempM3U8Intent(context, intent, result)
        }

        val position = getViewPos(video.id)?.position ?: 0L

        // MX Player specific extras
        intent.putExtra("return_result", true) // Request playback result
        intent.putExtra("position", position.toInt()) // Position in milliseconds
        intent.putExtra("title", video.name)
        intent.putExtra("secure_uri", true)
        
        // Decode mode: 1=HW, 2=SW, 4=HW+
        // Let MX Player auto-select by not setting this
        
        // Subtitles support
        val subsLang = getKey(SUBTITLE_AUTO_SELECT_KEY) ?: "en"
        val matchingSub = result.subs.firstOrNull { subsLang == it.languageCode }
        if (matchingSub != null) {
            // MX Player supports subtitle URIs
            intent.putExtra("subs", arrayOf(matchingSub.url.toUri()))
            intent.putExtra("subs.name", arrayOf(matchingSub.name))
        }
    }

    override fun onResult(activity: Activity, intent: Intent?) {
        // MX Player returns position and duration as integers in milliseconds
        val position = intent?.getIntExtra("position", -1) ?: -1
        val duration = intent?.getIntExtra("duration", -1) ?: -1
        val endBy = intent?.getStringExtra("end_by") // "user", "playback_completion", etc.
        
        Log.d("MXPlayer", "Position: $position, Duration: $duration, EndBy: $endBy")
        updateDurationAndPosition(position.toLong(), duration.toLong())
    }
}
