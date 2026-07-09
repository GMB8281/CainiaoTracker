package com.marinov.cainiaotracker.util

object ActiveViewingManager {
    private var currentTrackingCode: String? = null

    fun setViewing(trackingCode: String) {
        currentTrackingCode = trackingCode
    }

    fun clearViewing() {
        currentTrackingCode = null
    }

    fun isViewing(trackingCode: String): Boolean {
        return currentTrackingCode == trackingCode
    }
}