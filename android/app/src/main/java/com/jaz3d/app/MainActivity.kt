package com.jaz3d.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jaz3d.app.model.CharacterProject
import com.jaz3d.app.render.CharacterSurface
import com.jaz3d.app.storage.ProjectStore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ProjectStore(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                CharacterEditor(store)
            }
        }
    }
}

@Composable
private fun CharacterEditor(store: ProjectStore) {
    var project by remember {
        mutableStateOf(store.load() ?: CharacterProject())
    }

    var status by remember {
        mutableStateOf("Ready · Offline")
    }

    var surface by remember {
        mutableStateOf<CharacterSurface?>(null)
    }

    val wide = androidx.compose.ui.platform
        .LocalConfiguration.current.screenWidthDp >= 700

    fun update(change: (CharacterProject) -> CharacterProject) {
        project = change(project)
        surface?.characterRenderer?.character = project
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Jaz3D Character Studio")
                },
                actions = {
                    TextButton(
                        onClick = {
                            try {
                                store.save(project)
                                status = "Project saved locally"
                            } catch (e: Exception) {
                                status = "Save failed: ${e.message}"
                            }
                        }
                    ) {
                        Text("Save")
                    }

                    TextButton(
                        onClick = {
                            try {
                                val loaded = store.load()
                                if (loaded != null) {
                                    project = loaded
                                    surface?.characterRenderer?.character = loaded
                                    status = "Project loaded"
                                } else {
                                    status = "No saved project found"
                                }
                            } catch (e: Exception) {
                                status = "Load failed: ${e.message}"
                            }
                        }
                    ) {
                        Text("Load")
                    }
                }
            )
        }
    ) { padding ->
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1.5f)) {
                    CharacterViewport(
                        project,
                        { surface = it },
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(status)
                }

                MorphControls(
                    project = project,
                    onUpdate = ::update,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            ) {
                CharacterViewport(
                    project,
                    { surface = it },
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )

                Spacer(Modifier.height(8.dp))
                Text(status)
                Spacer(Modifier.height(8.dp))

                MorphControls(
                    project,
                    ::update,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CharacterViewport(
    project: CharacterProject,
    onSurface: (CharacterSurface) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CharacterSurface(context).also {
                it.characterRenderer.character = project
                onSurface(it)
            }
        },
        update = {
            it.characterRenderer.character = project
        }
    )
}

@Composable
private fun MorphControls(
    project: CharacterProject,
    onUpdate: ((CharacterProject) -> CharacterProject) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            "Character Parameters",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        Text("Body")

        MorphSlider("Height", project.height) {
            onUpdate { p -> p.copy(height = it) }
        }

        MorphSlider("Build", project.build) {
            onUpdate { p -> p.copy(build = it) }
        }

        MorphSlider("Shoulder width", project.shoulderWidth) {
            onUpdate { p -> p.copy(shoulderWidth = it) }
        }

        MorphSlider("Hip width", project.hipWidth) {
            onUpdate { p -> p.copy(hipWidth = it) }
        }

        MorphSlider("Torso length", project.torsoLength) {
            onUpdate { p -> p.copy(torsoLength = it) }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text("Face")

        MorphSlider("Head size", project.headSize) {
            onUpdate { p -> p.copy(headSize = it) }
        }

        MorphSlider("Jaw width", project.jawWidth) {
            onUpdate { p -> p.copy(jawWidth = it) }
        }

        MorphSlider("Eye spacing", project.eyeSpacing) {
            onUpdate { p -> p.copy(eyeSpacing = it) }
        }

        MorphSlider("Nose width", project.noseWidth) {
            onUpdate { p -> p.copy(noseWidth = it) }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text("Appearance")

        MorphSlider("Skin tone", project.skinTone) {
            onUpdate { p -> p.copy(skinTone = it) }
        }
    }
}

@Composable
private fun MorphSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}%")
        }

        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f
        )
    }
}
