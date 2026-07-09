package com.marinov.cainiaotracker.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PackageRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cainiao_prefs", Context.MODE_PRIVATE)
    private val keyPackages = "packages"

    fun getAllPackages(): List<Package> {
        val jsonString = prefs.getString(keyPackages, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val packages = mutableListOf<Package>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            packages.add(
                Package(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    trackingCode = obj.getString("trackingCode"),
                    isArchived = obj.getBoolean("isArchived")
                )
            )
        }
        return packages
    }

    fun savePackages(packages: List<Package>) {
        val jsonArray = JSONArray()
        for (pkg in packages) {
            val obj = JSONObject()
            obj.put("id", pkg.id)
            obj.put("name", pkg.name)
            obj.put("trackingCode", pkg.trackingCode)
            obj.put("isArchived", pkg.isArchived)
            jsonArray.put(obj)
        }
        prefs.edit().putString(keyPackages, jsonArray.toString()).apply()
    }

    fun addPackage(pkg: Package) {
        val list = getAllPackages().toMutableList()
        list.add(pkg)
        savePackages(list)
    }

    fun updatePackage(pkg: Package) {
        val list = getAllPackages().toMutableList()
        val index = list.indexOfFirst { it.id == pkg.id }
        if (index != -1) {
            list[index] = pkg
            savePackages(list)
        }
    }

    fun deletePackage(pkg: Package) {
        val list = getAllPackages().toMutableList()
        list.removeAll { it.id == pkg.id }
        savePackages(list)
        // Apaga o cache de rastreamento ao deletar a encomenda
        TrackingCacheManager.deleteTrackingInfo(context, pkg.trackingCode)
    }
}