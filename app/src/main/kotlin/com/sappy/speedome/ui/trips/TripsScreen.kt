package com.sappy.speedome.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.ui.EmptyState
import com.sappy.speedome.ui.Fmt
import com.sappy.speedome.ui.theme.SpeedoColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TripsScreen() {
    val app = LocalAppContainer.current
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val openId = open
    if (openId != null) {
        BackHandler { open = null }
        TripDetail(openId, onBack = { open = null })
        return
    }
    val trips by remember { app.db.trips().trips() }.collectAsStateWithLifecycle(emptyList())
    if (trips.isEmpty()) {
        EmptyState(title = "No trips yet", body = "Press Record on the Speed tab and your trips will be listed here.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Trips", style = MaterialTheme.typography.headlineSmall, color = SpeedoColors.Text, modifier = Modifier.padding(bottom = 8.dp)) }
        items(trips, key = { it.id }) { t -> TripRow(t) { open = t.id } }
    }
}

@Composable
private fun TripRow(t: SessionEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RouteThumbnail(t.thumbnail, Modifier.size(64.dp))
        Column(Modifier.weight(1f)) {
            Text(tripTitle(t), color = SpeedoColors.Text, fontSize = 16.sp)
            Text(tripSummary(t), color = SpeedoColors.Muted, fontSize = 13.sp)
        }
    }
}

fun tripDate(t: SessionEntity): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(t.startedAt))

fun tripTitle(t: SessionEntity): String = t.name?.takeIf { it.isNotBlank() } ?: tripDate(t)

fun tripSummary(t: SessionEntity): String {
    val avg = if (t.movingS > 1) Fmt.speed(t.distanceM / t.movingS) else 0.0
    val walk = if (t.mode == Mode.STEP.name) "Walk · " else ""
    return "$walk${Fmt.dist(t.distanceM)} ${Fmt.distUnit} · ${Fmt.duration(t.elapsedS)} · avg ${Fmt.decimal(avg, 0)} · max ${Fmt.decimal(Fmt.speed(t.maxMps), 0)}"
}
