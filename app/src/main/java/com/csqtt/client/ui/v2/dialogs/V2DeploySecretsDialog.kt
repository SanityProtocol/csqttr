@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.csqtt.client.ui.v2.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csqtt.client.SettingsStore
import com.csqtt.client.ui.legacy.utils.TUNNEL_PASSWORD_RULE
import com.csqtt.client.ui.legacy.utils.generate16CharPassword
import com.csqtt.client.ui.legacy.utils.isValidTunnelPassword
import com.csqtt.client.ui.v2.components.V2CircleIconButton
import com.csqtt.client.ui.v2.components.V2PillButton
import com.csqtt.client.ui.v2.components.V2PillStyle
import com.csqtt.client.ui.v2.components.V2SquareIconButton
import com.csqtt.client.ui.v2.components.V2TextField
import com.csqtt.client.ui.v2.design.V2BodyFamily
import com.csqtt.client.ui.v2.design.V2Spacing
import com.csqtt.client.ui.v2.design.V2Theme
import kotlinx.coroutines.launch

@Composable
fun V2DeploySecretsDialog(
    settingsStore: SettingsStore,
    initialMainPass: String,
    initialSshLogin: String,
    initialSshPass: String,
    initialWebLogin: String,
    initialWebPass: String,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = V2Theme.colors

    var mainPassInput by rememberSaveable {
        mutableStateOf(initialMainPass.ifBlank { generate16CharPassword() })
    }
    var webLoginInput by rememberSaveable { mutableStateOf(initialWebLogin) }
    var webPassInput by rememberSaveable { mutableStateOf(initialWebPass) }
    var passwordsVisible by rememberSaveable { mutableStateOf(false) }

    val allowedChars = Regex("^[a-zA-Z0-9]+$")

    val isWebLoginValid = webLoginInput.isNotEmpty() && webLoginInput.matches(allowedChars)
    val isWebPassValid = webPassInput.isNotEmpty() && webPassInput.matches(allowedChars)
    // Any password the server takes (2.5.2): not only 16 Latin letters and digits
    val isMainPassValid = isValidTunnelPassword(mainPassInput)
    val canSave = isWebLoginValid && isWebPassValid && isMainPassValid

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun animateDismiss(onDone: () -> Unit = onDismiss) {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDone()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.bottomSurface,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = colors.textMuted.copy(alpha = 0.4f),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = V2Spacing.Lg)
                .padding(bottom = V2Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(V2Spacing.Sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Авторизация",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = V2BodyFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                V2CircleIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Закрыть",
                    onClick = { animateDismiss() },
                )
            }

            Text(
                text = "Придумайте логин и пароль для web панели (латиница и цифры) и пароль туннеля.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = V2BodyFamily,
                    fontSize = 13.sp,
                ),
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Пароль подключения к туннелю",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = V2BodyFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                color = colors.accent,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(V2Spacing.Sm),
            ) {
                V2TextField(
                    value = mainPassInput,
                    onValueChange = {
                        mainPassInput = it.filter { c -> !c.isWhitespace() }
                    },
                    placeholder = "Пароль туннеля",
                    isError = mainPassInput.isNotEmpty() && !isMainPassValid,
                    modifier = Modifier.weight(1f),
                    visualTransformation = if (passwordsVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                V2SquareIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Сгенерировать пароль",
                    onClick = { mainPassInput = generate16CharPassword() },
                    size = 54.dp,
                    iconSize = 22.dp,
                )
            }

            if (mainPassInput.isNotEmpty() && !isMainPassValid) {
                Text(
                    text = TUNNEL_PASSWORD_RULE,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = V2BodyFamily),
                    color = colors.error,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Данные доступа к web панели",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = V2BodyFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                color = colors.accent,
            )

            V2TextField(
                value = webLoginInput,
                onValueChange = {
                    webLoginInput = it.filter { c -> !c.isWhitespace() }
                },
                placeholder = "Логин WEB (придумайте)",
                isError = webLoginInput.isNotEmpty() && !isWebLoginValid,
            )

            V2TextField(
                value = webPassInput,
                onValueChange = {
                    webPassInput = it.filter { c -> !c.isWhitespace() }
                },
                placeholder = "Пароль WEB (придумайте)",
                isError = webPassInput.isNotEmpty() && !isWebPassValid,
                visualTransformation = if (passwordsVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { passwordsVisible = !passwordsVisible },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(V2Spacing.Sm),
            ) {
                Icon(
                    imageVector = if (passwordsVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (passwordsVisible) "Скрыть пароли" else "Показать пароли",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = V2BodyFamily,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(V2Spacing.Sm),
            ) {
                V2PillButton(
                    text = "Отмена",
                    onClick = { animateDismiss() },
                    style = V2PillStyle.Outline,
                    modifier = Modifier.weight(1f),
                    height = 54.dp,
                )
                V2PillButton(
                    text = "Сохранить",
                    onClick = {
                        animateDismiss {
                            scope.launch {
                                settingsStore.saveDeploySecrets(mainPassInput, initialSshLogin, initialSshPass, webLoginInput, webPassInput)
                                settingsStore.saveConnectionPassword(mainPassInput)
                                onSaved()
                                onDismiss()
                            }
                        }
                    },
                    style = V2PillStyle.Contrast,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    height = 54.dp,
                )
            }
        }
    }
}
