package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.Alert

@Composable
fun AlertScreen(
    alerts: List<Alert>,
    isLoading: Boolean,
    onResolveAlert: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var showResolved by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "\u26A0\uFE0F Cảnh báo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val unresolvedCount = alerts.count { !it.isResolved }
                    Text(
                        text = "$unresolvedCount cảnh báo chưa xử lý",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unresolvedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                }
            }
        }

        // Filter
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showResolved,
                onClick = { showResolved = false },
                label = { Text("Chưa xử lý") },
                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = showResolved,
                onClick = { showResolved = true },
                label = { Text("Đã xử lý") },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val filteredAlerts = alerts.filter { it.isResolved == showResolved }

        if (filteredAlerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (showResolved) Icons.Default.CheckCircle else Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (showResolved) "Chưa có cảnh báo đã xử lý" else "\uD83C\uDF89 Không có cảnh báo nào!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAlerts) { alert ->
                    AlertCard(alert = alert, onResolve = { onResolveAlert(alert.id) })
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: Alert, onResolve: () -> Unit) {
    var showResolveDialog by remember { mutableStateOf(false) }

    if (showResolveDialog) {
        AlertDialog(
            onDismissRequest = { showResolveDialog = false },
            title = { Text("Xử lý cảnh báo") },
            text = { Text("Đánh dấu cảnh báo '${alert.message}' đã được xử lý?") },
            confirmButton = {
                Button(onClick = {
                    onResolve()
                    showResolveDialog = false
                }) { Text("Đã xử lý") }
            },
            dismissButton = {
                TextButton(onClick = { showResolveDialog = false }) { Text("Hủy") }
            }
        )
    }

    val severityColor = when (alert.severity) {
        "CRITICAL" -> Color(0xFFD32F2F)
        "HIGH" -> Color(0xFFE65100)
        "MEDIUM" -> Color(0xFFFF9800)
        "LOW" -> Color(0xFF4CAF50)
        else -> Color.Gray
    }

    val severityIcon = when (alert.severity) {
        "CRITICAL" -> Icons.Default.Error
        "HIGH" -> Icons.Default.Warning
        "MEDIUM" -> Icons.Default.Info
        else -> Icons.Default.NotificationsNone
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (!alert.isResolved) severityColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                severityIcon,
                contentDescription = null,
                tint = if (!alert.isResolved) severityColor else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    alert.severity?.let {
                        AssistChip(
                            onClick = { },
                            label = { Text(it, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = severityColor.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                alert.deviceName?.let {
                    Text(
                        text = "\uD83D\uDCBB Thiết bị: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                alert.createdAt?.let {
                    Text(
                        text = "\uD83D\uDD52 ${it.take(19).replace('T', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (alert.isResolved && alert.resolvedAt != null) {
                    Text(
                        text = "\u2705 Đã xử lý: ${alert.resolvedAt.take(19).replace('T', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }
                if (!alert.isResolved) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showResolveDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đánh dấu đã xử lý")
                    }
                }
            }
        }
    }
}
