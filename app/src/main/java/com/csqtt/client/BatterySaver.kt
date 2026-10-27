package com.csqtt.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Экономия батареи» (off by default): at or below the chosen charge, and while the phone is not charging, the app
 * stops holding the CPU awake for the tunnel — the partial wake locks of TunnelService and TunVpnService. The Wi-Fi
 * locks stay. Measured on a phone (2.5.2, screen off, a night each): ~78 → ~65 mA, the CPU awake 100 % → 8 % of the
 * time; the price is a tunnel that sleeps between Android's maintenance windows (the server reached it ~1 in 7 times).
 */
object BatterySaver {
    const val DEFAULT_PERCENT = 30
    val PERCENT_CHOICES = listOf(10, 15, 20, 25, 30, 40, 50)

    const val TITLE = "Экономия батареи"

    /** The «i» text of both designs: what it saves and what it costs, in the user's terms. */
    const val DESCRIPTION =
        "Когда заряд опустится до выбранного уровня, а телефон не заряжается, CSQTT перестаёт держать процессор " +
            "телефона включённым ради туннеля.\n\n" +
            "С выключенным экраном телефон расходует заметно меньше заряда, но туннель засыпает вместе с ним: " +
            "сообщения, звонки и уведомления, которые идут через VPN, могут приходить с большой задержкой, а загрузки " +
            "останавливаются. Когда экран включается, туннель продолжает работу.\n\n" +
            "На зарядке и выше выбранного уровня всё работает как обычно. По умолчанию функция выключена."

    /** The main screen's badge while the saver is in force. */
    const val BADGE = "Экономия батареи: туннель может засыпать при выключенном экране"

    private val inForce = MutableStateFlow(false)

    /** True while the saver is in force: the tunnel service runs and has let go of its wake locks. */
    val active: StateFlow<Boolean> = inForce.asStateFlow()

    /** On, at or below the chosen charge, not charging. An unknown level (-1) never saves. */
    fun shouldSave(enabled: Boolean, percent: Int, level: Int, charging: Boolean): Boolean =
        enabled && !charging && level in 0..percent

    internal fun setActive(value: Boolean) {
        inForce.value = value
    }
}
