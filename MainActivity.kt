package com.jaz3d.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File

private val Ink = Color(0xFF101318)
private val Panel = Color(0xFF1A1F27)
private val Panel2 = Color(0xFF242B35)
private val Accent = Color(0xFF8DB8FF)
private val Muted = Color(0xFFABB5C3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink, surface = Panel, primary = Accent,
                onBackground = Color.White, onSurface = Color.White
            )) {
                CharacterStudioScreen(
                    onSave = { profile ->
                        File(filesDir, "character-profile.json").writeText(profile.toString(2))
                    },
                    onLoad = {
                        val f = File(filesDir, "character-profile.json")
                        if (f.exists()) JSONObject(f.readText()) else JSONObject()
                    }
                )
            }
        }
    }
}

@Composable
private fun CharacterStudioScreen(onSave: (JSONObject) -> Unit, onLoad: () -> JSONObject) {
    var selectedTab by remember { mutableStateOf("Body") }
    var status by remember { mutableStateOf("Local project · offline ready") }
    var characterName by remember { mutableStateOf("Character 01") }
    val morphs = remember { mutableStateMapOf(
        "Height" to 50f, "Build" to 50f, "Shoulder width" to 50f,
        "Torso length" to 50f, "Hip width" to 50f, "Arm length" to 50f,
        "Jaw width" to 50f, "Eye spacing" to 50f, "Nose width" to 50f
    ) }

    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    Scaffold(
        containerColor = Ink,
        topBar = {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("JAZ3D", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Portable character workshop", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = {
                    val p = onLoad()
                    val character = p.optJSONObject("character") ?: p
                    if (character.has("name")) characterName = character.optString("name", characterName)
                    val values = character.optJSONObject("morphs")
                    val ids = mapOf(
                        "Height" to "height", "Build" to "build", "Shoulder width" to "shoulderWidth",
                        "Torso length" to "torsoLength", "Hip width" to "hipWidth", "Arm length" to "armLength",
                        "Jaw width" to "jawWidth", "Eye spacing" to "eyeSpacing", "Nose width" to "noseWidth"
                    )
                    if (values != null) ids.forEach { (label, id) ->
                        if (values.has(id)) morphs[label] = (values.optDouble(id, 0.5) * 100.0).toFloat().coerceIn(0f, 100f)
                    }
                    status = if (p.length() > 0) "Project loaded" else "No saved project yet"
                }) { Icon(Icons.Default.FolderOpen, contentDescription = "Load local project") }
                IconButton(onClick = {
                    val ids = mapOf(
                        "Height" to "height", "Build" to "build", "Shoulder width" to "shoulderWidth",
                        "Torso length" to "torsoLength", "Hip width" to "hipWidth", "Arm length" to "armLength",
                        "Jaw width" to "jawWidth", "Eye spacing" to "eyeSpacing", "Nose width" to "noseWidth"
                    )
                    val values = JSONObject()
                    ids.forEach { (label, id) -> values.put(id, (morphs[label] ?: 50f).toDouble() / 100.0) }
                    val character = JSONObject().put("name", characterName).put("morphs", values)
                    val p = JSONObject().put("schemaVersion", 1).put("project", "Jaz3D").put("character", character)
                    onSave(p); status = "Saved on this device"
                }) { Icon(Icons.Default.Save, contentDescription = "Save project") }
            }
        }
    ) { padding ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(padding).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CharacterHeader(characterName, { characterName = it })
                    ViewportPlaceholder(Modifier.weight(1f).fillMaxWidth())
                    ActionRow(morphs, { status = it })
                    Text(status, color = Muted, fontSize = 12.sp)
                }
                MorphPanel(selectedTab, { selectedTab = it }, morphs, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CharacterHeader(characterName, { characterName = it })
                ViewportPlaceholder(Modifier.fillMaxWidth().height(300.dp))
                ActionRow(morphs, { status = it })
                Text(status, color = Muted, fontSize = 12.sp)
                MorphPanel(selectedTab, { selectedTab = it }, morphs, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}


@Composable
private fun CharacterHeader(name: String, onNameChange: (String) -> Unit) {
    OutlinedTextField(
        value = name, onValueChange = onNameChange,
        label = { Text("Character name") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Panel2
        )
    )
}

@Composable
private fun ViewportPlaceholder(modifier: Modifier = Modifier) {
    // Intentionally a placeholder until the native 3D renderer and mesh pipeline are added.
    Box(modifier.background(Panel, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ViewInAr, contentDescription = null, tint = Accent, modifier = Modifier.size(54.dp))
            Text("3D VIEWPORT", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Renderer + base mesh are next", color = Muted, fontSize = 12.sp)
            Text("Foldable-aware workspace", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ActionRow(morphs: MutableMap<String, Float>, setStatus: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { setStatus("Undo is planned for the editing milestone") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Undo, null); Spacer(Modifier.width(4.dp)); Text("Undo") }
        FilledTonalButton(onClick = { morphs.keys.forEach { morphs[it] = 50f }; setStatus("Morphs reset") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Reset") }
    }
}

@Composable
private fun MorphPanel(tab: String, onTab: (String) -> Unit, morphs: MutableMap<String, Float>, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text("EDIT CHARACTER", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(12.dp))
        ScrollableTabRow(selectedTabIndex = listOf("Body", "Face", "Anatomy", "Materials").indexOf(tab), containerColor = Panel, contentColor = Accent, edgePadding = 0.dp) {
            listOf("Body", "Face", "Anatomy", "Materials").forEach { name -> Tab(selected = tab == name, onClick = { onTab(name) }, text = { Text(name) }) }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val controls = when (tab) {
                "Face" -> listOf("Jaw width", "Eye spacing", "Nose width")
                "Anatomy" -> listOf("Torso length", "Hip width", "Shoulder width")
                "Materials" -> emptyList()
                else -> listOf("Height", "Build", "Shoulder width", "Torso length", "Hip width", "Arm length")
            }
            if (tab == "Materials") {
                Text("Material controls will arrive with the rendering pipeline.", color = Muted, fontSize = 13.sp)
            } else {
                controls.forEach { key ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(key, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Text("${morphs[key]?.toInt() ?: 50}%", color = Accent, fontSize = 12.sp)
                        }
                        Slider(value = morphs[key] ?: 50f, onValueChange = { morphs[key] = it }, valueRange = 0f..100f)
                    }
                }
            }
        }
        Text("MVP · parameter UI only", color = Muted, fontSize = 11.sp)
    }
}
