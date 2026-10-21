package com.jaz3d.app.model

import org.json.JSONObject

data class CharacterProject(
    val name: String = "Character 01",
    val height: Float = 1.0f,
    val build: Float = 0.5f,
    val shoulderWidth: Float = 0.5f,
    val hipWidth: Float = 0.5f,
    val torsoLength: Float = 0.5f,
    val headSize: Float = 0.5f,
    val jawWidth: Float = 0.5f,
    val eyeSpacing: Float = 0.5f,
    val noseWidth: Float = 0.5f,
    val skinTone: Float = 0.65f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("format", "jaz3d-character")
        put("name", name)

        put("morphs", JSONObject().apply {
            put("height", height.toDouble())
            put("build", build.toDouble())
            put("shoulderWidth", shoulderWidth.toDouble())
            put("hipWidth", hipWidth.toDouble())
            put("torsoLength", torsoLength.toDouble())
            put("headSize", headSize.toDouble())
            put("jawWidth", jawWidth.toDouble())
            put("eyeSpacing", eyeSpacing.toDouble())
            put("noseWidth", noseWidth.toDouble())
            put("skinTone", skinTone.toDouble())
        })
    }

    companion object {
        fun fromJson(json: JSONObject): CharacterProject {
            val data = json.optJSONObject("character") ?: json
            val morphs = data.optJSONObject("morphs") ?: data

            fun number(key: String, default: Float) =
                morphs.optDouble(key, default.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)

            return CharacterProject(
                name = data.optString("name", "Character 01"),
                height = number("height", 0.5f),
                build = number("build", 0.5f),
                shoulderWidth = number("shoulderWidth", 0.5f),
                hipWidth = number("hipWidth", 0.5f),
                torsoLength = number("torsoLength", 0.5f),
                headSize = number("headSize", 0.5f),
                jawWidth = number("jawWidth", 0.5f),
                eyeSpacing = number("eyeSpacing", 0.5f),
                noseWidth = number("noseWidth", 0.5f),
                skinTone = number("skinTone", 0.65f)
            )
        }
    }
}
