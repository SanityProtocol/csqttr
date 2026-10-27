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
    // «Авто» (the default): with the phone's own battery saver. «Свой уровень»: at or below the chosen charge.
    const val MODE_AUTO = "auto"
    const val MODE_LEVEL = "level"
    const val MODE_OFF = "off"
    const val DEFAULT_MODE = MODE_AUTO

    /** The modes as both designs offer them, in this order. */
    val MODES = listOf(MODE_AUTO to "Авто", MODE_LEVEL to "Свой уровень", MODE_OFF to "Выкл")

    const val DEFAULT_PERCENT = 30
    val PERCENT_CHOICES = listOf(10, 15, 20, 25, 30, 40, 50)

    const val TITLE = "Экономия батареи"
    const val LEVEL_TITLE = "Включать при заряде"

    /** The «i» text of both designs: what it saves and what it costs, in the user's terms. */
    const val DESCRIPTION =
        "Когда экономия включена, CSQTT перестаёт держать процессор телефона включённым ради туннеля.\n\n" +
            "С выключенным экраном телефон расходует заметно меньше заряда, но туннель засыпает вместе с ним: " +
            "сообщения, звонки и уведомления, которые идут через VPN, могут приходить с большой задержкой, а загрузки " +
            "останавливаются. Когда экран включается, туннель продолжает работу.\n\n" +
            "Авто — экономия работает, пока включён режим энергосбережения телефона. Свой уровень — когда заряд " +
            "опустится до выбранного значения, а телефон не заряжается. Выкл — туннель никогда не засыпает."

    /** The main screen's badge while the saver is in force. */
    const val BADGE = "Экономия батареи: туннель может засыпать при выключенном экране"

    private val inForce = MutableStateFlow(false)

    /** True while the saver is in force: the tunnel service runs and has let go of its wake locks. */
    val active: StateFlow<Boolean> = inForce.asStateFlow()

    /**
     * «Авто»: while the phone's battery saver is on. «Свой уровень»: at or below the chosen charge and not charging; an
     * unknown level (-1) never saves. «Выкл» (or an unknown mode): never.
     */
    fun shouldSave(mode: String, percent: Int, level: Int, charging: Boolean, systemSaver: Boolean): Boolean =
        when (mode) {
            MODE_AUTO -> systemSaver
            MODE_LEVEL -> !charging && level in 0..percent
            else -> false
        }

    internal fun setActive(value: Boolean) {
        inForce.value = value
    }
}
