
package com.jaz3d.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jaz3d.app.model.CharacterProject
import com.jaz3d.app.render.CharacterSurface
import com.jaz3d.app.storage.ProjectStore

class MainActivity : ComponentActivity() {

    private lateinit var projectStore: ProjectStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectStore = ProjectStore(applicationContext)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CharacterEditor(store = projectStore)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterEditor(store: ProjectStore) {
    var project by remember {
        mutableStateOf(store.load() ?: CharacterProject())
    }

    var statusMessage by remember { mutableStateOf("Ready") }
    var refreshKey by remember { mutableStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Jaz3D")
                        Text(
                            text = "Character Studio",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            try {
                                store.save(project)
                                statusMessage = "Project saved"
                            } catch (exception: Exception) {
                                statusMessage =
                                    "Save failed: ${exception.message ?: "unknown error"}"
                            }
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save project",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }

                    TextButton(
                        onClick = {
                            try {
                                val loaded = store.load()
                                if (loaded != null) {
                                    project = loaded
                                    refreshKey++
                                    statusMessage = "Project loaded"
                                } else {
                                    statusMessage = "No saved project found"
                                }
                            } catch (exception: Exception) {
                                statusMessage =
                                    "Load failed: ${exception.message ?: "unknown error"}"
                            }
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Load project",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Load")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Drag the viewport to rotate the mannequin.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))

                        key(refreshKey) {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                factory = { context ->
                                    CharacterSurface(context)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "Body",
                            style = MaterialTheme.typography.titleMedium
                        )

                        MorphSlider("Height", project.height) {
                            project = project.copy(height = it)
                        }
                        MorphSlider("Build", project.build) {
                            project = project.copy(build = it)
                        }
                        MorphSlider("Shoulder width", project.shoulderWidth) {
                            project = project.copy(shoulderWidth = it)
                        }
                        MorphSlider("Hip width", project.hipWidth) {
                            project = project.copy(hipWidth = it)
                        }
                        MorphSlider("Torso length", project.torsoLength) {
                            project = project.copy(torsoLength = it)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "Face",
                            style = MaterialTheme.typography.titleMedium
                        )

                        MorphSlider("Head size", project.headSize) {
                            project = project.copy(headSize = it)
                        }
                        MorphSlider("Jaw width", project.jawWidth) {
                            project = project.copy(jawWidth = it)
                        }
                        MorphSlider("Eye spacing", project.eyeSpacing) {
                            project = project.copy(eyeSpacing = it)
                        }
                        MorphSlider("Nose width", project.noseWidth) {
                            project = project.copy(noseWidth = it)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium
                        )

                        MorphSlider("Skin tone", project.skinTone) {
                            project = project.copy(skinTone = it)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    try {
                                        store.save(project)
                                        statusMessage = "Project saved locally"
                                    } catch (exception: Exception) {
                                        statusMessage =
                                            "Save failed: ${exception.message ?: "unknown error"}"
                                    }
                                }
                            ) {
                                Text("Save project")
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    project = CharacterProject()
                                    refreshKey++
                                    statusMessage = "New character created"
                                }
                            ) {
                                Text("New character")
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Changes are held in memory until saved. " +
                                "The current renderer uses a simple mannequin.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MorphSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.labelMedium
            )
        }

        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { newValue ->
                onValueChange(newValue.coerceIn(0f, 1f))
            },
            valueRange = 0f..1f
        )
    }
}
