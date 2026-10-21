package com.jaz3d.app.storage

import android.content.Context
import com.jaz3d.app.model.CharacterProject
import org.json.JSONObject
import java.io.File

class ProjectStore(context: Context) {
    private val directory = File(context.filesDir, "projects")
        .apply { mkdirs() }

    fun save(project: CharacterProject): File {
        val file = File(directory, "current-character.json")
        val temp = File(directory, "current-character.tmp")

        temp.writeText(
            JSONObject()
                .put("schemaVersion", 1)
                .put("character", project.toJson())
                .toString(2)
        )

        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Unable to replace project")
        }

        if (!temp.renameTo(file)) {
            throw IllegalStateException("Unable to save project")
        }

        return file
    }

    fun load(): CharacterProject? {
        val file = File(directory, "current-character.json")
        if (!file.exists()) return null

        return CharacterProject.fromJson(
            JSONObject(file.readText())
        )
    }

    fun exportJson(project: CharacterProject): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("character", project.toJson())
            .toString(2)

    fun importJson(json: String): CharacterProject {
        return CharacterProject.fromJson(JSONObject(json))
    }
}
