package com.csqtt.client.ui.v2.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csqtt.client.SshHostKeyStore
import com.csqtt.client.ui.v2.components.V2DialogContainer
import com.csqtt.client.ui.v2.components.V2PillButton
import com.csqtt.client.ui.v2.components.V2PillStyle
import com.csqtt.client.ui.v2.design.V2BodyFamily
import com.csqtt.client.ui.v2.design.V2Spacing
import com.csqtt.client.ui.v2.design.V2Theme

/**
 * The server presented another SSH key than the one remembered at the first connection. Normal after a VPS reinstall
 * or when the address now belongs to another server; otherwise someone may be answering in the server's place. Shows
 * both fingerprints; «Доверять новому ключу» remembers the new one (and the action runs again), «Отмена» changes nothing.
 * Before this the app refused the server for good, with no way to accept its new key short of clearing the app's data.
 */
@Composable
fun V2HostKeyChangedDialog(
    change: SshHostKeyStore.KeyChange,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
) {
    val colors = V2Theme.colors

    V2DialogContainer(
        title = "SSH-ключ сервера изменился",
        subtitle = "${change.host}:${change.port}",
        icon = Icons.Filled.Key,
        onDismiss = onDismiss,
        actions = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(V2Spacing.Sm),
            ) {
                V2PillButton(
                    text = "Доверять новому ключу",
                    onClick = onTrust,
                    style = V2PillStyle.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                )
                V2PillButton(
                    text = "Отмена",
                    onClick = onDismiss,
                    style = V2PillStyle.Outline,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                )
            }
        },
    ) {
        Text(
            text = "Сервер предъявил другой SSH-ключ, чем при первом подключении. " +
                "Так бывает после переустановки VPS или если этот адрес теперь у другого сервера.\n\n" +
                "Если вы VPS не переустанавливали — не продолжайте: соединение может перехватываться.",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = V2BodyFamily, lineHeight = 18.sp),
            color = colors.textSecondary,
        )
        Fingerprint("Был", change.pinned)
        Fingerprint("Сейчас", change.presented)
    }
}

@Composable
private fun Fingerprint(label: String, value: String) {
    val colors = V2Theme.colors
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = V2BodyFamily),
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 16.sp),
            color = colors.textPrimary,
        )
    }
}
