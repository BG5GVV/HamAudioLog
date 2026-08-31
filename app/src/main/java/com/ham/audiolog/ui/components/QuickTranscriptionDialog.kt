package com.ham.audiolog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ham.audiolog.data.model.AudioMarkerEntity

@Composable
fun QuickTranscriptionDialog(
    marker: AudioMarkerEntity,
    onDismiss: () -> Unit,
    onConfirm: (AudioMarkerEntity) -> Unit
) {
    var callsign by remember { mutableStateOf(marker.callsign) }
    var rstSent by remember { mutableStateOf(marker.rstSent) }
    var rstRcvd by remember { mutableStateOf(marker.rstRcvd) }
    var band by remember { mutableStateOf(marker.band) }
    var mode by remember { mutableStateOf(marker.mode) }
    var remark by remember { mutableStateOf(marker.remark) }

    val offsetSec = marker.audioOffsetMs / 1000
    val timeOffsetStr = "%02d:%02d".format(offsetSec / 60, offsetSec % 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "补录标记 #${marker.markerIndex} ($timeOffsetStr)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "UTC 时间: ${marker.localFormattedTime} · 偏移: ${timeOffsetStr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = callsign,
                    onValueChange = { callsign = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '/' } },
                    label = { Text("对方呼号 (Callsign)") },
                    placeholder = { Text("如: BA1AA, BH4XYZ/7") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rstSent,
                        onValueChange = { rstSent = it },
                        label = { Text("Sent") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rstRcvd,
                        onValueChange = { rstRcvd = it },
                        label = { Text("Rcvd") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = band,
                        onValueChange = { band = it },
                        label = { Text("波段 (如 40m)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mode,
                        onValueChange = { mode = it },
                        label = { Text("模式 (如 SSB)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注信息") },
                    placeholder = { Text("QTH/设备/备忘...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = marker.copy(
                        callsign = callsign.trim().uppercase(),
                        rstSent = rstSent.trim().ifBlank { "59" },
                        rstRcvd = rstRcvd.trim().ifBlank { "59" },
                        band = band.trim(),
                        mode = mode.trim(),
                        remark = remark.trim(),
                        isTranscribed = callsign.isNotBlank()
                    )
                    onConfirm(updated)
                }
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
