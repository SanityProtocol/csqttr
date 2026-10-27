package com.csqtt.client.ui.legacy.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.csqtt.client.SshHostKeyStore
import com.csqtt.client.ui.legacy.appSectionCardBorderColor
import com.csqtt.client.ui.legacy.appSectionCardColor
import com.csqtt.client.ui.legacy.design.CsqttShapes
import com.csqtt.client.ui.legacy.design.CsqttSizes

/**
 * The server presented another SSH key than the one remembered at the first connection. Normal after a VPS reinstall
 * or when the address now belongs to another server; otherwise someone may be answering in the server's place. Shows
 * both fingerprints; «Доверять новому ключу» remembers the new one (and the check runs again), «Отмена» changes nothing.
 * Before this the app refused the server for good, with no way to accept its new key short of clearing the app's data.
 */
@Composable
internal fun HostKeyChangedDialog(
    change: SshHostKeyStore.KeyChange,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = CsqttShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "SSH-ключ сервера изменился",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Сервер ${change.host}:${change.port} предъявил другой SSH-ключ, чем при первом подключении. " +
                        "Так бывает после переустановки VPS или если этот адрес теперь у другого сервера.\n\n" +
                        "Если вы VPS не переустанавливали — не продолжайте: соединение может перехватываться.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Fingerprint("Был", change.pinned)
                Fingerprint("Сейчас", change.presented)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = CsqttSizes.ControlHeight),
                        shape = CsqttShapes.Pill,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text("Отмена", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onTrust,
                        modifier = Modifier.weight(1f).heightIn(min = CsqttSizes.ControlHeight),
                        shape = CsqttShapes.Pill,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Доверять новому ключу", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fingerprint(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
    }
}
