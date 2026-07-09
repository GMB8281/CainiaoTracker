package com.marinov.cainiaotracker.data

import org.json.JSONArray
import org.json.JSONObject

data class Package(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val trackingCode: String,
    var isArchived: Boolean = false
)

data class TimelineEvent(
    val title: String,
    val description: String,
    val time: String
)

data class TrackingInfo(
    val status: String,
    val origin: String,
    val destination: String,
    val timeline: List<TimelineEvent>
) {
    // Converte o objeto para JSONObject para salvar no arquivo
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("status", status)
        json.put("origin", origin)
        json.put("destination", destination)
        val timelineArray = JSONArray()
        for (event in timeline) {
            val eventJson = JSONObject()
            eventJson.put("title", event.title)
            eventJson.put("description", event.description)
            eventJson.put("time", event.time)
            timelineArray.put(eventJson)
        }
        json.put("timeline", timelineArray)
        return json
    }
}

// Classe de retorno para a função de verificação de atualizações
data class UpdateResult(val hasUpdate: Boolean, val latestTitle: String)

// Extensão para converter o JSON lido do arquivo de volta para o objeto
fun JSONObject.toTrackingInfo(): TrackingInfo {
    val status = optString("status", "")
    val origin = optString("origin", "")
    val destination = optString("destination", "")
    val timelineArray = optJSONArray("timeline") ?: JSONArray()
    val timeline = mutableListOf<TimelineEvent>()
    for (i in 0 until timelineArray.length()) {
        val eventJson = timelineArray.getJSONObject(i)
        timeline.add(
            TimelineEvent(
                title = eventJson.optString("title", ""),
                description = eventJson.optString("description", ""),
                time = eventJson.optString("time", "")
            )
        )
    }
    return TrackingInfo(status, origin, destination, timeline)
}