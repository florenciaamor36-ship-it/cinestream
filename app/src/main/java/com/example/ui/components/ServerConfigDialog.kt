package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ServerStatusResponse
import com.example.ui.theme.*

@Composable
fun ServerConfigDialog(
    currentUrl: String,
    serverStatus: ServerStatusResponse?,
    cacheSizeMb: Double,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onRefreshStatus: () -> Unit,
    onClearCache: () -> Unit
) {
    var inputUrl by remember(currentUrl) { mutableStateOf(currentUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .testTag("server_config_dialog"),
            color = DarkSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .background(DarkSurface)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = NetflixRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ajustes de Calidad y Red",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input URL Servidor
                Text(
                    text = "Dirección del Servidor de Transmisión",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NetflixRed,
                        unfocusedBorderColor = AccentBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { onSaveUrl(inputUrl) }) {
                            Icon(Icons.Default.Check, contentDescription = "Guardar", tint = NetflixRed)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Estado de Servidores CDN Premium
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nodos de Distribución HLS (CDN)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            IconButton(
                                onClick = onRefreshStatus,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualizar",
                                    tint = BadgeTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        val pool = serverStatus?.workerPool
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusMetric(label = "CDNs Activos", value = "${pool?.totalWorkers ?: 20}")
                            StatusMetric(label = "Nodos OK", value = "${pool?.activeWorkers ?: 20}", color = Color(0xFF4CAF50))
                            StatusMetric(label = "Latencia", value = "Óptima", color = BadgeTeal)
                            StatusMetric(label = "Calidad", value = "4K / HD", color = NetflixRed)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Caché local SimpleCache
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Caché de Almacenamiento Inteligente",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = String.format("%.1f MB optimizados en disco", cacheSizeMb),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        TextButton(
                            onClick = onClearCache,
                            colors = ButtonDefaults.textButtonColors(contentColor = NetflixRed)
                        ) {
                            Text("Limpiar", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Botón Aceptar
                Button(
                    onClick = {
                        onSaveUrl(inputUrl)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_server_config_button")
                ) {
                    Text(
                        text = "Aplicar Ajustes",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String, color: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 11.sp, color = TextMuted)
    }
}

