package com.csqtt.client.ui.legacy.dialogs

import androidx.compose.foundation.BorderStroke
import com.csqtt.client.ui.legacy.appSectionCardBorderColor
import com.csqtt.client.ui.legacy.appSectionCardColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.csqtt.client.TurnSubnetRegistry
import com.csqtt.client.ui.legacy.design.CsqttShapes
import com.csqtt.client.ui.legacy.design.CsqttSizes
import com.csqtt.client.ui.legacy.CompactDropdownSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.roundToInt

@Composable
internal fun TurnSubnetsDialog(
    selectedSubnetId: String,
    turnSubnetIpLimit: String = "ALL",
    onSelectSubnet: (String) -> Unit,
    onSelectIpLimit: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var currentIpLimit by remember(turnSubnetIpLimit) { mutableStateOf(turnSubnetIpLimit) }
    val limitInt = currentIpLimit.toIntOrNull()

    val pingResults = remember { mutableStateMapOf<String, Int?>() }
    val pingStatus = remember { mutableStateMapOf<String, Boolean>() }
    var isPinging by remember { mutableStateOf(false) }

    val eligibleSubnets = TurnSubnetRegistry.subnets.filter { limitInt == null || it.count >= limitInt }

    val sortedEligibleIds = eligibleSubnets
        .mapNotNull { subnet ->
            val ping = pingResults[subnet.id]
            if (ping != null && ping > 0) subnet.id to ping else null
        }
        .sortedBy { it.second }
        .map { it.first }

    fun getPingColor(subnetId: String): Color {
        if (isPinging) return Color(0xFF1475FD)
        val ping = pingResults[subnetId] ?: return Color.Unspecified
        if (ping <= 0) return Color(0xFFE53935)
        val rank = sortedEligibleIds.indexOf(subnetId)
        val total = sortedEligibleIds.size
        if (rank == -1 || total == 0) return Color(0xFFE53935)
        val greenThreshold = (total + 2) / 3
        val yellowThreshold = (2 * total + 2) / 3
        return when {
            rank < greenThreshold -> Color(0xFF4CAF50)
            rank < yellowThreshold -> Color(0xFFFFA000)
            else -> Color(0xFFE53935)
        }
    }

    fun startPingCheck() {
        if (isPinging) return
        coroutineScope.launch {
            isPinging = true
            pingResults.clear()
            pingStatus.clear()
            eligibleSubnets.forEach { subnet ->
                pingStatus[subnet.id] = true
                val rtt = withContext(Dispatchers.IO) {
                    measureSubnetRtt(subnet.ips)
                }
                pingResults[subnet.id] = rtt?.roundToInt() ?: -1
                pingStatus[subnet.id] = false
            }
            isPinging = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = CsqttShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Выбор TURN подсетей",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = "Выберите подсеть для распределения потоков. Если все выключены — используются адреса по умолчанию из ответа VK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TurnSubnetRegistry.subnets.forEach { subnet ->
                        val isEligible = limitInt == null || subnet.count >= limitInt
                        val isChecked = isEligible && selectedSubnetId == subnet.id
                        Surface(
                            shape = CsqttShapes.Card,
                            color = when {
                                !isEligible -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    !isEligible -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    isChecked -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isEligible) {
                                    onSelectSubnet(if (isChecked) "" else subnet.id)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subnet.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isEligible) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val poolSubtitle = when {
                                        subnet.count % 10 == 1 && subnet.count % 100 != 11 -> "${subnet.count} сервер в пуле"
                                        subnet.count % 10 in 2..4 && subnet.count % 100 !in 12..14 -> "${subnet.count} сервера в пуле"
                                        else -> "${subnet.count} серверов в пуле"
                                    }
                                    Text(
                                        text = poolSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isEligible) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                        },
                                    )
                                }
                                if (isEligible) {
                                    val ping = pingResults[subnet.id]
                                    val isCurrentPinging = pingStatus[subnet.id] == true
                                    if (isCurrentPinging) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    } else if (ping != null) {
                                        Text(
                                            text = if (ping > 0) "$ping мс" else "—",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = getPingColor(subnet.id),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                }
                                Switch(
                                    checked = isChecked,
                                    enabled = isEligible,
                                    onCheckedChange = { checked ->
                                        if (isEligible) {
                                            onSelectSubnet(if (checked) subnet.id else "")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                CompactDropdownSetting(
                    title = "Колл-во используемых IP",
                    selectedKey = currentIpLimit,
                    options = listOf(
                        "ALL" to "ВСЕ",
                        "1" to "1",
                        "2" to "2",
                        "4" to "4",
                        "9" to "9",
                    ),
                    enabled = true,
                    onSelected = { newLimit: String ->
                        currentIpLimit = newLimit
                        onSelectIpLimit(newLimit)
                        val newLimitInt = newLimit.toIntOrNull()
                        if (newLimitInt != null) {
                            val currentSubnet = TurnSubnetRegistry.getSubnet(selectedSubnetId)
                            if (currentSubnet == null || currentSubnet.count < newLimitInt) {
                                val firstMatch = TurnSubnetRegistry.subnets.firstOrNull { it.count >= newLimitInt }
                                if (firstMatch != null) {
                                    onSelectSubnet(firstMatch.id)
                                }
                            }
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { startPingCheck() },
                        enabled = !isPinging,
                        shape = CsqttShapes.Pill,
                        border = BorderStroke(1.dp, Color(0xFF1475FD)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1475FD),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(CsqttSizes.ControlHeight),
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1475FD),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Пинг...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Проверить пинг", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        shape = CsqttShapes.Pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1475FD),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(CsqttSizes.ControlHeight),
                    ) {
                        Text("Сохранить", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun measureSubnetRtt(ips: List<String>): Double? {
    if (ips.isEmpty()) return null
    val first3 = ips.take(3)
    val last3 = ips.takeLast(3)
    val firstRtts = first3.mapNotNull { probeStunRtt(it) }
    val lastRtts = last3.mapNotNull { probeStunRtt(it) }
    val avg1 = if (firstRtts.isNotEmpty()) firstRtts.average() else null
    val avg2 = if (lastRtts.isNotEmpty()) lastRtts.average() else null
    return when {
        avg1 != null && avg2 != null -> (avg1 + avg2) / 2.0
        avg1 != null -> avg1
        avg2 != null -> avg2
        else -> null
    }
}

private fun probeStunRtt(ip: String): Double? {
    return try {
        val socket = DatagramSocket()
        socket.soTimeout = 300
        val req = byteArrayOf(0, 1, 0, 0, 0x21, 0x12, 0xa4.toByte(), 0x42) + ByteArray(12) { (0..255).random().toByte() }
        val addr = InetAddress.getByName(ip)
        val t0 = System.nanoTime()
        socket.send(DatagramPacket(req, req.size, addr, 19302))
        val resp = ByteArray(64)
        socket.receive(DatagramPacket(resp, resp.size))
        val t1 = System.nanoTime()
        socket.close()
        (t1 - t0) / 1_000_000.0
    } catch (_: Exception) {
        null
    }
}
