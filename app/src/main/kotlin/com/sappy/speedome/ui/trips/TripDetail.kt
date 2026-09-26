package com.sappy.speedome.ui.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.ui.TripStatsGrid
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data object Loading : Loaded

    data object Gone : Loaded

    data class Trip(val session: SessionEntity) : Loaded
}

@Composable
fun TripDetail(id: Long, onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val dao = app.db.trips()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by remember(id) {
        flow {
            emit(Loaded.Loading)
            dao.observeSession(id).collect { emit(if (it == null) Loaded.Gone else Loaded.Trip(it)) }
        }
    }.collectAsStateWithLifecycle(Loaded.Loading)
    val points by remember(id) { dao.observePoints(id) }.collectAsStateWithLifecycle(emptyList())
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(loaded) { if (loaded == Loaded.Gone) onBack() }
    val t = (loaded as? Loaded.Trip)?.session ?: return

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GpxExport.MIME)) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { GpxExport.writeTo(context, uri, t, points) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Trips", color = SpeedoColors.Accent) }
        Column {
            Text(tripTitle(t), color = SpeedoColors.Text, fontSize = 22.sp)
            if (t.name != null) Text(tripDate(t), color = SpeedoColors.Muted, fontSize = 13.sp)
        }
        TripStatsGrid(t)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TripMap(points, Modifier.fillMaxWidth().aspectRatio(1.15f))
            if (points.size >= 2) SpeedLegend(maxSpeedOf(points), Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        }
        Text("SPEED", color = SpeedoColors.Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        SpeedGraph(points, Modifier.fillMaxWidth().height(150.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = points.isNotEmpty(), onClick = {
                scope.launch {
                    val intent = withContext(Dispatchers.IO) { GpxExport.shareIntent(context, t, points) }
                    context.startActivity(intent)
                }
            }) { Text("Share GPX") }
            OutlinedButton(enabled = points.isNotEmpty(), onClick = { saveAs.launch(GpxExport.fileName(t)) }) { Text("Save GPX") }
            OutlinedButton(onClick = { renaming = true }) { Text("Rename") }
            OutlinedButton(onClick = { deleting = true }) { Text("Delete", color = Color(0xFFFF6B5E)) }
        }
    }

    if (renaming) RenameDialog(t.name.orEmpty(), onDismiss = { renaming = false }) { name ->
        renaming = false
        scope.launch { dao.rename(id, name.trim().ifEmpty { null }) }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this trip?") },
            text = { Text("${tripTitle(t)} will be removed from your phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch { dao.deleteSession(id) }
                }) { Text("Delete", color = Color(0xFFFF6B5E)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename trip") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(60) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
