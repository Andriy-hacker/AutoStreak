package com.example.tiktokautostreak

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MessageType {
    TEXT,
    EMOJI
}

data class ContactConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val customText: String = "🔥 streak",
    val emojiSymbol: String = "❤️"
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("messageType", messageType.name)
            put("customText", customText)
            put("emojiSymbol", emojiSymbol)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ContactConfig {
            return ContactConfig(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", ""),
                messageType = try {
                    MessageType.valueOf(json.optString("messageType", "TEXT"))
                } catch (e: Exception) {
                    MessageType.TEXT
                },
                customText = json.optString("customText", "🔥 streak"),
                emojiSymbol = json.optString("emojiSymbol", "❤️")
            )
        }

        fun toJsonList(list: List<ContactConfig>): String {
            val jsonArray = JSONArray()
            list.forEach { jsonArray.put(it.toJson()) }
            return jsonArray.toString()
        }

        fun fromJsonList(jsonString: String): List<ContactConfig> {
            if (jsonString.isEmpty()) return emptyList()
            val list = mutableListOf<ContactConfig>()
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    list.add(fromJson(jsonArray.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Fallback for legacy comma-separated string
                val names = jsonString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                return names.map { ContactConfig(name = it) }
            }
            return list
        }
    }
}