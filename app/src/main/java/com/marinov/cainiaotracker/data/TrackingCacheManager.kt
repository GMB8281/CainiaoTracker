package com.marinov.cainiaotracker.data

import android.content.Context
import org.json.JSONObject
import java.io.File

object TrackingCacheManager {
    private fun getCacheFile(context: Context, trackingCode: String): File {
        return File(context.filesDir, "track_cache_$trackingCode.json")
    }

    fun saveTrackingInfo(context: Context, trackingCode: String, info: TrackingInfo) {
        val file = getCacheFile(context, trackingCode)
        file.writeText(info.toJson().toString())
    }

    fun loadTrackingInfo(context: Context, trackingCode: String): TrackingInfo? {
        val file = getCacheFile(context, trackingCode)
        if (!file.exists()) return null
        return try {
            val jsonString = file.readText()
            JSONObject(jsonString).toTrackingInfo()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteTrackingInfo(context: Context, trackingCode: String) {
        val file = getCacheFile(context, trackingCode)
        if (file.exists()) {
            file.delete()
        }
    }
}