package com.csqtt.client

import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONObject

private fun <T> Flow<T>.onIo(): Flow<T> = flowOn(Dispatchers.IO)

internal data class DeploySettingsSnapshot(
    val profile: Int = -1,
    val ip: String = "",
    val sshLogin: String = "",
    val sshPassword: String = "",
    val webLogin: String = "",
    val webPassword: String = "",
    val mainPassword: String = "",
    val sshPort: String = CsqttConstants.Network.DEFAULT_SSH_PORT.toString(),
    val manualPortsEnabled: Boolean = false,
    val serverPeerPort: Int = CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT,
    val serverWebPort: Int = CsqttConstants.Network.DEFAULT_SERVER_WEB_PORT,
    val sshKeysMode: Boolean = false,
    val dockerInstall: Boolean = false,
    val deployInstance: String = "side",
    val secretPathEnabled: Boolean = true,
    val secretPathUuid: String = "",
    val sshPrivateKey: String = "",
    val sshKeyPassphrase: String = "",
    val sshCertificate: String = "",
) {
    val isLoaded: Boolean
        get() = profile >= 0
}

internal data class TunnelAuthSnapshot(
    val profile: Int = -1,
    val connectionPassword: String = "",
    val connectionPasswordState: StoredSecretState = StoredSecretState.Missing,
    val csqttLink: String = "",
    val csqttLinkMode: Boolean = false,
) {
    val isLoaded: Boolean
        get() = profile >= 0
}

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    companion object {
        private val Context.dataStore by preferencesDataStore("settings")
        private val ACTIVE_PROFILE = intPreferencesKey("active_profile")
        private val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        private val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        private val INFO_ACTIONS_EXPANDED = booleanPreferencesKey("info_actions_expanded")
        private val INFO_PROJECT_EXPANDED = booleanPreferencesKey("info_project_expanded")
        private val CSQTT_LINK = stringPreferencesKey("csqtt_link")
        private val CSQTT_LINK_MODE = booleanPreferencesKey("csqtt_link_mode")
        private val PARTICIPANT_SHOW_LOGS = booleanPreferencesKey("participant_show_logs")

        private val PEER = stringPreferencesKey("peer")
        private val VK_HASHES = stringPreferencesKey("vk_hashes")
        private val SECONDARY_VK_HASH = stringPreferencesKey("secondary_vk_hash")
        private val WORKERS_PER_HASH = intPreferencesKey("workers_per_hash")
        private val PROTOCOL = stringPreferencesKey("protocol")
        private val LISTEN_PORT = intPreferencesKey("listen_port")
        private val MANUAL_PORTS_ENABLED = booleanPreferencesKey("manual_ports_enabled")
        private val SERVER_PEER_PORT = intPreferencesKey("server_peer_port")
        private val SNI = stringPreferencesKey("sni")
        private val NO_DNS = booleanPreferencesKey("no_dns")
        private val CUSTOM_TURN_IPS = stringPreferencesKey("custom_turn_ips")
        private val SELECTED_TURN_SUBNET = stringPreferencesKey("selected_turn_subnet")
        private val TURN_SUBNET_IP_LIMIT = stringPreferencesKey("turn_subnet_ip_limit")

        private val USER_AGENT = stringPreferencesKey("user_agent")
        private val PROFILE_NAME = stringPreferencesKey("profile_name")

        private val DEPLOY_IP = stringPreferencesKey("deploy_ip")
        private val DEPLOY_LOGIN = stringPreferencesKey("deploy_login")
        private val DEPLOY_PASSWORD = stringPreferencesKey("deploy_password")
        private val DEPLOY_PASSWORD_ENCRYPTED = stringPreferencesKey("deploy_password_encrypted")
        private val DEPLOY_SSH_PORT = stringPreferencesKey("deploy_ssh_port")
        private val SSH_KEYS_MODE = booleanPreferencesKey("ssh_keys_mode")
        private val DOCKER_INSTALL = booleanPreferencesKey("docker_install")
        private val DEPLOY_INSTANCE = stringPreferencesKey("deploy_instance")   // "side" (absent) | "main"
        private val DEPLOY_SECRET_PATH_ENABLED = booleanPreferencesKey("deploy_secret_path_enabled")
        private val DEPLOY_SECRET_PATH_UUID = stringPreferencesKey("deploy_secret_path_uuid")
        private val SSH_PRIVATE_KEY = stringPreferencesKey("ssh_private_key")
        private val SSH_PRIVATE_KEY_ENCRYPTED = stringPreferencesKey("ssh_private_key_encrypted")
        private val SSH_KEY_PASSPHRASE = stringPreferencesKey("ssh_key_passphrase")
        private val SSH_KEY_PASSPHRASE_ENCRYPTED = stringPreferencesKey("ssh_key_passphrase_encrypted")
        private val SSH_CERTIFICATE = stringPreferencesKey("ssh_certificate")
        private val SSH_CERTIFICATE_ENCRYPTED = stringPreferencesKey("ssh_certificate_encrypted")
        private val EXCLUDED_APPS = stringPreferencesKey("excluded_apps")
        private val WHITELIST_APPS = stringPreferencesKey("whitelist_apps")

        private val DETAILED_LOGS = booleanPreferencesKey("detailed_logs")

        private val CONNECTION_PASSWORD = stringPreferencesKey("connection_password")
        private val CONNECTION_PASSWORD_ENCRYPTED = stringPreferencesKey("connection_password_encrypted")
        private val CONNECTION_PASSWORD_LOCAL = stringPreferencesKey("connection_password_local")
        private val DEPLOY_MAIN_PASSWORD = stringPreferencesKey("deploy_main_password")
        private val DEPLOY_MAIN_PASSWORD_ENCRYPTED = stringPreferencesKey("deploy_main_password_encrypted")
        private val DEPLOY_WEB_LOGIN = stringPreferencesKey("deploy_web_login")
        private val DEPLOY_WEB_PASSWORD = stringPreferencesKey("deploy_web_password")
        private val DEPLOY_WEB_PASSWORD_ENCRYPTED = stringPreferencesKey("deploy_web_password_encrypted")
        private val SERVER_WEB_PORT = intPreferencesKey("server_web_port")

        private val PROXY_MODE = stringPreferencesKey("proxy_mode") 
        private val PROXY_HOST = stringPreferencesKey("proxy_host")
        private val PROXY_PORT = intPreferencesKey("proxy_port")

        private val VK_AUTH_MODE = stringPreferencesKey("vk_auth_mode") 
        private val OBFS_MODE = stringPreferencesKey("obfs_mode") 
        private val TURN_TRANSPORT = stringPreferencesKey("turn_transport")
        private val SRTP_MODE = stringPreferencesKey("srtp_mode")
        private val CAPTCHA_MODE = stringPreferencesKey("captcha_mode")
        private val CAPTCHA_SOLVE_METHOD = stringPreferencesKey("captcha_solve_method") 
        private val CAPTCHA_WBV_SOLVE_METHOD = stringPreferencesKey("captcha_wbv_solve_method") 

        private val VK_HASH_MODE = stringPreferencesKey("vk_hash_mode")
        private val VK_ACCESS_TOKEN = stringPreferencesKey("vk_access_token")
        private val VK_ACCESS_TOKEN_ENCRYPTED = stringPreferencesKey("vk_access_token_encrypted")
        private val VK_ACCESS_TOKEN_USER_ID = stringPreferencesKey("vk_access_token_user_id")

        private val IS_WHITELIST = booleanPreferencesKey("is_whitelist")
        private val SPLIT_TUNNEL_WHITELIST_MIGRATED = booleanPreferencesKey("split_tunnel_whitelist_migrated")
        private val EXTRA_WORKERS = booleanPreferencesKey("extra_workers")
        private val AUTO_JS_RISK_ACKNOWLEDGED = booleanPreferencesKey("auto_js_risk_acknowledged")
        private val TCP_TRANSPORT_RISK_ACKNOWLEDGED = booleanPreferencesKey("tcp_transport_risk_acknowledged")
        private val BATTERY_OPTIMIZATION_PROMPT_HANDLED =
            booleanPreferencesKey("battery_optimization_prompt_handled")
        private val AUTO_PAUSE_ON_WIFI = booleanPreferencesKey("auto_pause_on_wifi")
        private val DOMAIN_ROUTING_ENABLED = booleanPreferencesKey("domain_routing_enabled")
        private val DOMAIN_IS_WHITELIST = booleanPreferencesKey("domain_is_whitelist")
        private val EXCLUDE_RU_ZONES_ENABLED = booleanPreferencesKey("exclude_ru_zones_enabled")
        private val TEMPLATE_DOMAINS_ENABLED = booleanPreferencesKey("template_domains_enabled")
        private val DIRECT_DOMAINS = stringPreferencesKey("direct_domains")
        private val DIRECT_DOMAINS_BLACK = stringPreferencesKey("direct_domains_black")
        private val DIRECT_DOMAINS_WHITE = stringPreferencesKey("direct_domains_white")

        private val IP_ROUTING_ENABLED = booleanPreferencesKey("ip_routing_enabled")
        private val IP_IS_WHITELIST = booleanPreferencesKey("ip_is_whitelist")
        private val DIRECT_IPS_BLACK = stringPreferencesKey("direct_ips_black")
        private val DIRECT_IPS_WHITE = stringPreferencesKey("direct_ips_white")

        private val THEME_MODE = stringPreferencesKey("theme_mode") 
        private val IS_DYNAMIC_COLOR = booleanPreferencesKey("is_dynamic_color")
        private val THEME_PALETTE = stringPreferencesKey("theme_palette")

        private val SELECTED_FINGERPRINT = stringPreferencesKey("selected_fingerprint")
        private val ACTIVE_CLIENT_IDS = stringPreferencesKey("active_client_ids")

        private val UPDATE_LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
        private val UPDATE_LATEST_VERSION = stringPreferencesKey("update_latest_version")
        private val UPDATE_LAST_ERROR = stringPreferencesKey("update_last_error")
        private val UPDATE_CHECK_INTERVAL_HOURS = intPreferencesKey("update_check_interval_hours")
        private val UPDATE_POSTPONE_UNTIL = longPreferencesKey("update_postpone_until")
        private val UPDATE_POSTPONE_VERSION = stringPreferencesKey("update_postpone_version")
        private val UPDATE_DIALOG_LAST_SHOWN_VERSION = stringPreferencesKey("update_dialog_last_shown_version")
        private val UPDATE_DIALOG_LAST_SHOWN_AT = longPreferencesKey("update_dialog_last_shown_at")
        private val UPDATE_DIALOG_LAST_ACTION_VERSION = stringPreferencesKey("update_dialog_last_action_version")
        private val UPDATE_DIALOG_LAST_ACTION = stringPreferencesKey("update_dialog_last_action")
        private val UPDATE_DIALOG_LAST_ACTION_AT = longPreferencesKey("update_dialog_last_action_at")

        private val CLIENT_ID_CHECK_RESULTS = stringPreferencesKey("client_id_check_results")
        private val VK_HASH_CHECK_RESULTS = stringPreferencesKey("vk_hash_check_results")
        private val CONNECTION_GENERATION = longPreferencesKey("connection_generation")
        private val TUNNEL_LAST_DIAGNOSTIC = stringPreferencesKey("tunnel_last_diagnostic")
        private val TUNNEL_LAST_DIAGNOSTIC_AT = longPreferencesKey("tunnel_last_diagnostic_at")
        private val TUNNEL_WAS_RUNNING = booleanPreferencesKey("tunnel_was_running")

        @Volatile
        internal var cachedVkHashMode: String? = null

        @Volatile
        internal var cachedVkAccessToken: String? = null

        @Volatile
        internal var cachedObfsMode: String? = null

        @Volatile
        internal var cachedTurnTransport: String? = null

        @Volatile
        internal var cachedSrtpMode: String? = null

        @Volatile
        internal var cachedCsqttLinkMode: Boolean? = null

        @Volatile
        internal var cachedWorkersPerHash: Int? = null

        @Volatile
        internal var cachedManualPortsEnabled: Boolean? = null

        @Volatile
        internal var cachedServerPeerPort: Int? = null

        @Volatile
        internal var cachedExtraWorkers: Boolean? = null

        @Volatile
        internal var cachedCustomTurnIps: String? = null

        @Volatile
        internal var cachedExcludedApps: String? = null

        @Volatile
        internal var cachedWhitelistApps: String? = null

        @Volatile
        internal var cachedIsWhitelist: Boolean? = null

        @Volatile
        internal var cachedDomainRoutingEnabled: Boolean? = null

        @Volatile
        internal var cachedDomainIsWhitelist: Boolean? = null

        @Volatile
        internal var cachedExcludeRuZonesEnabled: Boolean? = null

        @Volatile
        internal var cachedTemplateDomainsEnabled: Boolean? = null

        @Volatile
        internal var cachedDirectDomains: String? = null

        @Volatile
        internal var cachedDirectDomainsBlack: String? = null

        @Volatile
        internal var cachedDirectDomainsWhite: String? = null

        @Volatile
        internal var cachedIpRoutingEnabled: Boolean? = null

        @Volatile
        internal var cachedIpIsWhitelist: Boolean? = null

        @Volatile
        internal var cachedDirectIpsBlack: String? = null

        @Volatile
        internal var cachedDirectIpsWhite: String? = null

        private val uiCacheStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        private val migrationsStarted = java.util.concurrent.atomic.AtomicBoolean(false)

        private fun <T> getProfileKey(baseKey: Preferences.Key<T>, profile: Int): Preferences.Key<T> {
            if (profile == 0) return baseKey
            val newName = "${baseKey.name}_$profile"
            @Suppress("UNCHECKED_CAST")
            return when (baseKey) {
                PEER, VK_HASHES, SECONDARY_VK_HASH, PROTOCOL, SNI, USER_AGENT, DEPLOY_IP, DEPLOY_LOGIN, DEPLOY_PASSWORD, DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_SSH_PORT, EXCLUDED_APPS, WHITELIST_APPS, DIRECT_DOMAINS, DIRECT_DOMAINS_BLACK, DIRECT_DOMAINS_WHITE, DIRECT_IPS_BLACK, DIRECT_IPS_WHITE, CONNECTION_PASSWORD, CONNECTION_PASSWORD_ENCRYPTED, CONNECTION_PASSWORD_LOCAL, DEPLOY_MAIN_PASSWORD, DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_WEB_LOGIN, DEPLOY_WEB_PASSWORD, DEPLOY_WEB_PASSWORD_ENCRYPTED, PROXY_MODE, PROXY_HOST, VK_AUTH_MODE, OBFS_MODE, TURN_TRANSPORT, SRTP_MODE, CAPTCHA_MODE, CAPTCHA_SOLVE_METHOD, CAPTCHA_WBV_SOLVE_METHOD, CSQTT_LINK, SELECTED_FINGERPRINT, ACTIVE_CLIENT_IDS, VK_HASH_MODE, VK_ACCESS_TOKEN, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN_USER_ID, SSH_PRIVATE_KEY, SSH_PRIVATE_KEY_ENCRYPTED, SSH_KEY_PASSPHRASE, SSH_KEY_PASSPHRASE_ENCRYPTED, SSH_CERTIFICATE, SSH_CERTIFICATE_ENCRYPTED, VK_HASH_CHECK_RESULTS, CLIENT_ID_CHECK_RESULTS, CUSTOM_TURN_IPS, SELECTED_TURN_SUBNET -> stringPreferencesKey(newName) as Preferences.Key<T>
                WORKERS_PER_HASH, LISTEN_PORT, SERVER_PEER_PORT, PROXY_PORT, SERVER_WEB_PORT -> intPreferencesKey(newName) as Preferences.Key<T>
                MANUAL_PORTS_ENABLED, NO_DNS, IS_WHITELIST, CSQTT_LINK_MODE, DETAILED_LOGS, SSH_KEYS_MODE, DOCKER_INSTALL, EXTRA_WORKERS, SPLIT_TUNNEL_WHITELIST_MIGRATED, DOMAIN_ROUTING_ENABLED, DOMAIN_IS_WHITELIST, EXCLUDE_RU_ZONES_ENABLED, TEMPLATE_DOMAINS_ENABLED, IP_ROUTING_ENABLED, IP_IS_WHITELIST -> booleanPreferencesKey(newName) as Preferences.Key<T>
                CONNECTION_GENERATION -> longPreferencesKey(newName) as Preferences.Key<T>
                else -> stringPreferencesKey(newName) as Preferences.Key<T>
            }
        }
    }

    private val dataStore = appContext.dataStore
    private val secureStore = SecureStringStore(appContext)

    init {
        if (migrationsStarted.compareAndSet(false, true)) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                migrateConnectionPasswordsToLocalStorage()
                migrateSecretsToKeystore()
                migrateLegacyWhitelistMode()
            }
        }
        if (uiCacheStarted.compareAndSet(false, true)) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                launch {
                    dataStore.edit { prefs ->
                        for (p in CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX) {
                            prefs[getProfileKey(EXTRA_WORKERS, p)] = false
                        }
                    }
                }
                launch { vkHashMode.collect { cachedVkHashMode = it } }
                launch { vkAccessToken.collect { cachedVkAccessToken = it } }
                launch { obfsMode.collect { cachedObfsMode = it } }
                launch { turnTransport.collect { cachedTurnTransport = it } }
                launch { srtpMode.collect { cachedSrtpMode = it } }
                launch { csqttLinkMode.collect { cachedCsqttLinkMode = it } }
                launch { workersPerHash.collect { cachedWorkersPerHash = it } }
                launch { manualPortsEnabled.collect { cachedManualPortsEnabled = it } }
                launch { serverPeerPort.collect { cachedServerPeerPort = it } }
                cachedExtraWorkers = false
                launch { customTurnIps.collect { cachedCustomTurnIps = it } }
                launch { excludedApps.collect { cachedExcludedApps = it } }
                launch { whitelistApps.collect { cachedWhitelistApps = it } }
                launch { isWhitelist.collect { cachedIsWhitelist = it } }
                launch { domainRoutingEnabled.collect { cachedDomainRoutingEnabled = it } }
                launch { domainIsWhitelist.collect { cachedDomainIsWhitelist = it } }
                launch { excludeRuZonesEnabled.collect { cachedExcludeRuZonesEnabled = it } }
                launch { templateDomainsEnabled.collect { cachedTemplateDomainsEnabled = it } }
                launch { directDomains.collect { cachedDirectDomains = it } }
                launch { directDomainsBlack.collect { cachedDirectDomainsBlack = it } }
                launch { directDomainsWhite.collect { cachedDirectDomainsWhite = it } }
                launch { ipRoutingEnabled.collect { cachedIpRoutingEnabled = it } }
                launch { ipIsWhitelist.collect { cachedIpIsWhitelist = it } }
                launch { directIpsBlack.collect { cachedDirectIpsBlack = it } }
                launch { directIpsWhite.collect { cachedDirectIpsWhite = it } }
            }
        }
    }

    val activeProfile: Flow<Int> = dataStore.data.map {
        (it[ACTIVE_PROFILE] ?: CsqttConstants.Profiles.MIN_INDEX).coerceIn(
            CsqttConstants.Profiles.MIN_INDEX,
            CsqttConstants.Profiles.MAX_INDEX,
        )
    }
    val tunnelLastDiagnostic: Flow<String> = dataStore.data.map {
        it[TUNNEL_LAST_DIAGNOSTIC] ?: ""
    }
    val showSystemApps: Flow<Boolean> = dataStore.data.map { it[SHOW_SYSTEM_APPS] ?: false }
    val loggingEnabled: Flow<Boolean> = dataStore.data.map { it[LOGGING_ENABLED] ?: true }
    val infoActionsExpanded: Flow<Boolean> = dataStore.data.map { it[INFO_ACTIONS_EXPANDED] ?: false }
    val infoProjectExpanded: Flow<Boolean> = dataStore.data.map { it[INFO_PROJECT_EXPANDED] ?: false }
    val csqttLink: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CSQTT_LINK, profile)] ?: ""
    }
    val csqttLinkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CSQTT_LINK_MODE, profile)] ?: false
    }
    val participantShowLogs: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(PARTICIPANT_SHOW_LOGS, profile)] ?: false
    }

    val peer: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(PEER, profile)] ?: ""
    }
    val vkHashes: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(VK_HASHES, profile)] ?: ""
    }
    val secondaryVkHash: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SECONDARY_VK_HASH, profile)] ?: ""
    }
    val workersPerHash: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val isExtra = prefs[getProfileKey(EXTRA_WORKERS, profile)] ?: false
        val max = WorkerCountPolicy.defaultMaximum(isExtra)
        val current = prefs[getProfileKey(WORKERS_PER_HASH, profile)] ?: 18
        WorkerCountPolicy.normalize(current, maximum = max)
    }
    val protocol: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(PROTOCOL, profile)] ?: "udp"
    }
    val listenPort: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(LISTEN_PORT, profile)] ?: 0
    }
    val manualPortsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] ?: false
    }
    val serverPeerPort: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SERVER_PEER_PORT, profile)] ?: CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT
    }
    val sni: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SNI, profile)] ?: ""
    }

    val deployIp: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_IP, profile)] ?: ""
    }
    val deployLogin: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_LOGIN, profile)] ?: ""
    }
    val deployPassword: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readSecret(prefs, DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_PASSWORD, profile)
    }.onIo()
    val deploySshPort: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_SSH_PORT, profile)] ?: ""
    }
    internal val deploySettingsSnapshot: Flow<DeploySettingsSnapshot> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        DeploySettingsSnapshot(
            profile = profile,
            ip = prefs[getProfileKey(DEPLOY_IP, profile)] ?: "",
            sshLogin = prefs[getProfileKey(DEPLOY_LOGIN, profile)] ?: "",
            sshPassword = readSecret(prefs, DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_PASSWORD, profile),
            webLogin = prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] ?: "",
            webPassword = readSecret(prefs, DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, profile),
            mainPassword = readSecret(prefs, DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, profile),
            sshPort = prefs[getProfileKey(DEPLOY_SSH_PORT, profile)]
                ?.ifBlank { CsqttConstants.Network.DEFAULT_SSH_PORT.toString() }
                ?: CsqttConstants.Network.DEFAULT_SSH_PORT.toString(),
            manualPortsEnabled = prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] ?: false,
            serverPeerPort = prefs[getProfileKey(SERVER_PEER_PORT, profile)]
                ?: CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT,
            serverWebPort = prefs[getProfileKey(SERVER_WEB_PORT, profile)]
                ?: CsqttConstants.Network.DEFAULT_SERVER_WEB_PORT,
            sshKeysMode = prefs[getProfileKey(SSH_KEYS_MODE, profile)] ?: false,
            dockerInstall = prefs[getProfileKey(DOCKER_INSTALL, profile)] ?: false,
            deployInstance = prefs[getProfileKey(DEPLOY_INSTANCE, profile)] ?: "side",
            secretPathEnabled = true,
            secretPathUuid = prefs[getProfileKey(DEPLOY_SECRET_PATH_UUID, profile)] ?: "",
            sshPrivateKey = readSecret(prefs, SSH_PRIVATE_KEY_ENCRYPTED, SSH_PRIVATE_KEY, profile),
            sshKeyPassphrase = readSecret(prefs, SSH_KEY_PASSPHRASE_ENCRYPTED, SSH_KEY_PASSPHRASE, profile),
            sshCertificate = readSecret(prefs, SSH_CERTIFICATE_ENCRYPTED, SSH_CERTIFICATE, profile),
        )
    }.onIo()
    internal val tunnelAuthSnapshot: Flow<TunnelAuthSnapshot> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val connectionPassword = readConnectionPasswordState(prefs, profile)
        TunnelAuthSnapshot(
            profile = profile,
            connectionPassword = connectionPassword.value,
            connectionPasswordState = connectionPassword.state,
            csqttLink = prefs[getProfileKey(CSQTT_LINK, profile)] ?: "",
            csqttLinkMode = prefs[getProfileKey(CSQTT_LINK_MODE, profile)] ?: false,
        )
    }.onIo()
    val excludedApps: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(EXCLUDED_APPS, profile)] ?: ""
    }
    val whitelistApps: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(WHITELIST_APPS, profile)] ?: ""
    }

    internal val connectionPasswordState: Flow<StoredSecret> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readConnectionPasswordState(prefs, profile)
    }.onIo()
    val connectionPassword: Flow<String> = connectionPasswordState.map { it.value }
    val deployMainPassword: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readSecret(prefs, DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, profile)
    }.onIo()
    val deployWebLogin: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] ?: ""
    }
    val deployWebPassword: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readSecret(prefs, DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, profile)
    }.onIo()
    val serverWebPort: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SERVER_WEB_PORT, profile)] ?: CsqttConstants.Network.DEFAULT_SERVER_WEB_PORT
    }

    val vkAuthMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        when (prefs[getProfileKey(VK_AUTH_MODE, profile)]) {
            CsqttConstants.VkAuth.MODE_CAPTCHA -> CsqttConstants.VkAuth.MODE_CAPTCHA
            CsqttConstants.VkAuth.MODE_AUTO_JS -> CsqttConstants.VkAuth.MODE_AUTO_JS
            else -> CsqttConstants.VkAuth.MODE_CALLS
        }
    }
    val obfsMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val mode = prefs[getProfileKey(OBFS_MODE, profile)]
        if (mode == "unified") {
            mode
        } else {
            CsqttConstants.Tunnel.DEFAULT_OBFS_MODE
        }
    }

    val turnTransport: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        when (prefs[getProfileKey(TURN_TRANSPORT, profile)]) {
            CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS -> CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
            else -> CsqttConstants.Tunnel.DEFAULT_TURN_TRANSPORT
        }
    }

    val srtpMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        when (prefs[getProfileKey(SRTP_MODE, profile)]) {
            CsqttConstants.SrtpMode.WRAP -> CsqttConstants.SrtpMode.WRAP
            CsqttConstants.SrtpMode.DTLS -> CsqttConstants.SrtpMode.DTLS
            else -> CsqttConstants.SrtpMode.DEFAULT
        }
    }

    val customTurnIps: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val subnetId = prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] ?: ""
        if (subnetId.isNotBlank()) {
            val stored = prefs[getProfileKey(CUSTOM_TURN_IPS, profile)]
            if (!stored.isNullOrBlank()) {
                stored
            } else {
                TurnSubnetRegistry.getJoinedIpsForSubnet(subnetId)
            }
        } else {
            ""
        }
    }

    val selectedTurnSubnet: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] ?: ""
    }

    val turnSubnetIpLimit: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] ?: "ALL"
    }

    val captchaMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CAPTCHA_MODE, profile)] ?: "auto"
    }
    val captchaSolveMethod: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CAPTCHA_SOLVE_METHOD, profile)] ?: "auto"
    }
    val captchaWbvSolveMethod: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CAPTCHA_WBV_SOLVE_METHOD, profile)] ?: "auto"
    }

    val vkHashMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val mode = prefs[getProfileKey(VK_HASH_MODE, profile)]
        when (mode) {
            "auto", CsqttConstants.VkAutoHash.MODE_AUTO_API ->
                CsqttConstants.VkAutoHash.MODE_AUTO_API
            CsqttConstants.VkAutoHash.MODE_AUTO_JS ->
                CsqttConstants.VkAutoHash.MODE_AUTO_JS
            else -> CsqttConstants.VkAutoHash.MODE_MANUAL
        }
    }
    val vkAccessToken: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, profile)
    }.onIo()
    val vkAccessTokenUserId: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile)] ?: ""
    }

    val isWhitelist: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(IS_WHITELIST, profile)] ?: false
    }

    val domainRoutingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DOMAIN_ROUTING_ENABLED, profile)] ?: false
    }

    val excludeRuZonesEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, profile)] ?: false
    }

    val templateDomainsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(TEMPLATE_DOMAINS_ENABLED, profile)] ?: false
    }

    val domainIsWhitelist: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DOMAIN_IS_WHITELIST, profile)] ?: false
    }

    val directDomains: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_DOMAINS, profile)] ?: ""
    }

    val directDomainsBlack: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_DOMAINS_BLACK, profile)]
            ?: prefs[getProfileKey(DIRECT_DOMAINS, profile)]
            ?: ""
    }

    val directDomainsWhite: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_DOMAINS_WHITE, profile)] ?: ""
    }

    val ipRoutingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(IP_ROUTING_ENABLED, profile)] ?: false
    }

    val ipIsWhitelist: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(IP_IS_WHITELIST, profile)] ?: false
    }

    val directIpsBlack: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_IPS_BLACK, profile)] ?: ""
    }

    val directIpsWhite: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_IPS_WHITE, profile)] ?: ""
    }

    val profileNames: Flow<Map<Int, String>> = dataStore.data.map { prefs ->
        (CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX).associateWith { p ->
            val name = prefs[getProfileKey(PROFILE_NAME, p)]
            if (name.isNullOrBlank()) "Профиль $p" else name
        }
    }

    suspend fun saveProfileName(profile: Int, name: String) {
        dataStore.edit { prefs ->
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed == "Профиль $profile") {
                prefs.remove(getProfileKey(PROFILE_NAME, profile))
            } else {
                prefs[getProfileKey(PROFILE_NAME, profile)] = trimmed
            }
        }
    }

    val extraWorkers: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    val autoJsRiskAcknowledged: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_JS_RISK_ACKNOWLEDGED] ?: false
    }
    val tcpTransportRiskAcknowledged: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[TCP_TRANSPORT_RISK_ACKNOWLEDGED] ?: false
    }
    val autoPauseOnWifi: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_PAUSE_ON_WIFI] ?: false
    }
    suspend fun isBatteryOptimizationPromptHandled(): Boolean =
        dataStore.data.first()[BATTERY_OPTIMIZATION_PROMPT_HANDLED] ?: false

    val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE] ?: "system" }
    val isDynamicColor: Flow<Boolean> = dataStore.data.map { it[IS_DYNAMIC_COLOR] ?: false }
    val themePalette: Flow<String> = dataStore.data.map { it[THEME_PALETTE] ?: "indigo" }

    val selectedFingerprint: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SELECTED_FINGERPRINT, profile)] ?: "firefox"
    }
    val activeClientIds: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(ACTIVE_CLIENT_IDS, profile)] ?: CsqttConstants.Tunnel.DEFAULT_CLIENT_IDS
    }
    val clientIdCheckResults: Flow<String> = dataStore.data.map { prefs ->
        prefs[CLIENT_ID_CHECK_RESULTS] ?: "{}"
    }
    val vkHashCheckResults: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(VK_HASH_CHECK_RESULTS, profile)] ?: "{}"
    }
    val connectionGeneration: Flow<Long> = dataStore.data.map { prefs ->
        prefs[CONNECTION_GENERATION] ?: 0L
    }

    val tunnelWasRunning: Flow<Boolean> = dataStore.data.map { it[TUNNEL_WAS_RUNNING] ?: false }
    val updateLastCheckAt: Flow<Long> = dataStore.data.map { it[UPDATE_LAST_CHECK_AT] ?: 0L }
    val updateLatestVersion: Flow<String> = dataStore.data.map { it[UPDATE_LATEST_VERSION] ?: "" }
    val updateLastError: Flow<String> = dataStore.data.map { it[UPDATE_LAST_ERROR] ?: "" }
    val updateCheckIntervalHours: Flow<Int> = dataStore.data.map { it[UPDATE_CHECK_INTERVAL_HOURS] ?: 24 }
    val updatePostponeUntil: Flow<Long> = dataStore.data.map { it[UPDATE_POSTPONE_UNTIL] ?: 0L }
    val updatePostponeVersion: Flow<String> = dataStore.data.map { it[UPDATE_POSTPONE_VERSION] ?: "" }

    suspend fun saveTunnelWasRunning(running: Boolean) {
        dataStore.edit { prefs ->
            prefs[TUNNEL_WAS_RUNNING] = running
        }
    }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    suspend fun saveDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[IS_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun saveThemePalette(palette: String) {
        dataStore.edit { prefs ->
            prefs[THEME_PALETTE] = palette
        }
    }

    suspend fun saveFingerprint(fingerprint: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(SELECTED_FINGERPRINT, profile)] = fingerprint
        }
    }

    suspend fun saveActiveClientIds(clientIds: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(ACTIVE_CLIENT_IDS, profile)] = clientIds
        }
    }

    suspend fun saveClientIdCheckResults(resultsJson: String) {
        dataStore.edit { prefs ->
            prefs[CLIENT_ID_CHECK_RESULTS] = resultsJson
        }
    }

    suspend fun saveVkHashCheckResults(resultsJson: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(VK_HASH_CHECK_RESULTS, profile)] = resultsJson
        }
    }

    suspend fun invalidateVkHash(hash: String) {
        val normalized = hash.trim()
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val key = getProfileKey(VK_HASH_CHECK_RESULTS, profile)
            val results = VkHashValidationCodec.decode(prefs[key] ?: "{}") +
                (normalized to VkHashValidationStatus.Invalid)
            prefs[key] = VkHashValidationCodec.encode(results)
        }
    }

    suspend fun reserveConnectionGeneration(
        proposed: Long = 0L,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): Long {
        var reserved = 0L
        dataStore.edit { prefs ->
            reserved = reserveTunnelGenerationId(
                nowSeconds.coerceAtLeast(0L),
                proposed.coerceAtLeast(0L),
                prefs[CONNECTION_GENERATION] ?: 0L,
            )
            prefs[CONNECTION_GENERATION] = reserved
        }
        return reserved
    }

    suspend fun recordConnectionGeneration(generation: Long): Long {
        var persisted = 0L
        dataStore.edit { prefs ->
            persisted = maxOf(prefs[CONNECTION_GENERATION] ?: 0L, generation.coerceAtLeast(0L))
            prefs[CONNECTION_GENERATION] = persisted
        }
        return persisted
    }

    suspend fun saveTunnelDiagnostic(code: String, message: String) {
        val normalizedCode = code.trim().take(80)
        val normalizedMessage = message.trim().replace(Regex("\\s+"), " ").take(400)
        dataStore.edit { prefs ->
            prefs[TUNNEL_LAST_DIAGNOSTIC] = "$normalizedCode|$normalizedMessage"
            prefs[TUNNEL_LAST_DIAGNOSTIC_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearTunnelDiagnostic() {
        dataStore.edit { prefs ->
            prefs.remove(TUNNEL_LAST_DIAGNOSTIC)
            prefs.remove(TUNNEL_LAST_DIAGNOSTIC_AT)
        }
    }

    suspend fun saveUpdateState(lastCheckAt: Long, latestVersion: String, error: String) {
        dataStore.edit { prefs ->
            prefs[UPDATE_LAST_CHECK_AT] = lastCheckAt
            prefs[UPDATE_LATEST_VERSION] = latestVersion
            prefs[UPDATE_LAST_ERROR] = error
        }
    }

    suspend fun saveUpdateCheckIntervalHours(hours: Int) {
        dataStore.edit { prefs ->
            prefs[UPDATE_CHECK_INTERVAL_HOURS] = hours
        }
    }

    suspend fun saveUpdatePostpone(version: String, until: Long) {
        dataStore.edit { prefs ->
            prefs[UPDATE_POSTPONE_VERSION] = version
            prefs[UPDATE_POSTPONE_UNTIL] = until
        }
    }

    suspend fun saveUpdateDialogShown(version: String, shownAt: Long) {
        dataStore.edit { prefs ->
            prefs[UPDATE_DIALOG_LAST_SHOWN_VERSION] = version
            prefs[UPDATE_DIALOG_LAST_SHOWN_AT] = shownAt
        }
    }

    suspend fun saveUpdateDialogAction(version: String, action: String, actedAt: Long) {
        dataStore.edit { prefs ->
            prefs[UPDATE_DIALOG_LAST_ACTION_VERSION] = version
            prefs[UPDATE_DIALOG_LAST_ACTION] = action
            prefs[UPDATE_DIALOG_LAST_ACTION_AT] = actedAt
        }
    }

    suspend fun saveActiveProfile(profile: Int) {
        dataStore.edit { prefs ->
            prefs[ACTIVE_PROFILE] = profile.coerceIn(
                CsqttConstants.Profiles.MIN_INDEX,
                CsqttConstants.Profiles.MAX_INDEX,
            )
        }
    }

    suspend fun saveShowSystemApps(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_SYSTEM_APPS] = enabled
        }
    }

    suspend fun saveLoggingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[LOGGING_ENABLED] = enabled
        }
    }

    suspend fun saveInfoActionsExpanded(expanded: Boolean) {
        dataStore.edit { prefs ->
            prefs[INFO_ACTIONS_EXPANDED] = expanded
        }
    }

    suspend fun saveInfoProjectExpanded(expanded: Boolean) {
        dataStore.edit { prefs ->
            prefs[INFO_PROJECT_EXPANDED] = expanded
        }
    }

    suspend fun saveCsqttLink(link: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CSQTT_LINK, profile)] = link
        }
    }

    suspend fun saveCsqttLinkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CSQTT_LINK_MODE, profile)] = enabled
        }
    }

    suspend fun saveCsqttParticipantLink(link: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CSQTT_LINK, profile)] = link
            prefs[getProfileKey(CSQTT_LINK_MODE, profile)] = true
        }
    }

    suspend fun saveParticipantShowLogs(show: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(PARTICIPANT_SHOW_LOGS, profile)] = show
        }
    }

    suspend fun saveWorkersPerHash(workersPerHash: Int) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(WORKERS_PER_HASH, profile)] = WorkerCountPolicy.normalize(workersPerHash, maximum = CsqttConstants.Tunnel.MAX_WORKERS)
        }
    }

    suspend fun saveParticipantTunnelSettings(vkHashes: String, workersPerHash: Int) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(VK_HASHES, profile)] = vkHashes
            prefs[getProfileKey(WORKERS_PER_HASH, profile)] =
                WorkerCountPolicy.normalize(workersPerHash, maximum = CsqttConstants.Tunnel.MAX_WORKERS)
        }
    }

    suspend fun save(
        peer: String,
        vkHashes: String,
        secondaryVkHash: String,
        workersPerHash: Int,
        protocol: String,
        listenPort: Int,
        sni: String = "",
        noDns: Boolean = false
    ) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(PEER, profile)] = peer
            prefs[getProfileKey(VK_HASHES, profile)] = vkHashes
            prefs[getProfileKey(SECONDARY_VK_HASH, profile)] = secondaryVkHash
            prefs[getProfileKey(WORKERS_PER_HASH, profile)] = WorkerCountPolicy.normalize(workersPerHash, maximum = CsqttConstants.Tunnel.MAX_WORKERS)
            prefs[getProfileKey(PROTOCOL, profile)] = protocol
            prefs[getProfileKey(LISTEN_PORT, profile)] = listenPort
            prefs[getProfileKey(SNI, profile)] = sni
            prefs[getProfileKey(NO_DNS, profile)] = noDns
        }
    }

    suspend fun saveManualPortsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] = enabled
            if (!enabled) {
                prefs[getProfileKey(DEPLOY_SSH_PORT, profile)] = CsqttConstants.Network.DEFAULT_SSH_PORT.toString()
                prefs[getProfileKey(SERVER_PEER_PORT, profile)] = CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT
                prefs[getProfileKey(SERVER_WEB_PORT, profile)] = CsqttConstants.Network.DEFAULT_SERVER_WEB_PORT
            }
        }
    }

    suspend fun saveSshKeysMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(SSH_KEYS_MODE, profile)] = enabled
        }
    }

    suspend fun saveDeployInstance(instance: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DEPLOY_INSTANCE, profile)] = instance
        }
    }

    // After a successful deploy the profile points at the server it installed: a server on ports other than the stock
    // ones (the «Рядом с 2.1.9» server's 46100/46102) turns "Ручные порты" on with them — the connection dials
    // serverPeerPort only then, and 46000 is the stock server's — and an empty server address becomes the host.
    // Nothing changes before a deploy succeeds.
    suspend fun saveDeployedServer(host: String, peerPort: Int, webPort: Int) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            if ((prefs[getProfileKey(PEER, profile)] ?: "").isBlank() && host.isNotBlank()) {
                prefs[getProfileKey(PEER, profile)] = host
            }
            if (peerPort != CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT || webPort != CsqttConstants.Network.DEFAULT_SERVER_WEB_PORT) {
                prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] = true
                prefs[getProfileKey(SERVER_PEER_PORT, profile)] = peerPort
                prefs[getProfileKey(SERVER_WEB_PORT, profile)] = webPort
            }
        }
    }

    suspend fun saveDockerInstall(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DOCKER_INSTALL, profile)] = enabled
        }
    }

    suspend fun saveDeploySecretPath(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val keyEnabled = getProfileKey(DEPLOY_SECRET_PATH_ENABLED, profile)
            val keyUuid = getProfileKey(DEPLOY_SECRET_PATH_UUID, profile)
            prefs[keyEnabled] = enabled
            if (enabled && prefs[keyUuid].isNullOrBlank()) {
                prefs[keyUuid] = UUID.randomUUID().toString()
            }
        }
    }

    suspend fun regenerateDeploySecretPathUuid(): String {
        val newUuid = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DEPLOY_SECRET_PATH_UUID, profile)] = newUuid
        }
        return newUuid
    }

    suspend fun saveSshKeys(privateKey: String, keyPassphrase: String, certificate: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs.putSecret(SSH_PRIVATE_KEY_ENCRYPTED, SSH_PRIVATE_KEY, privateKey, profile)
            prefs.putSecret(SSH_KEY_PASSPHRASE_ENCRYPTED, SSH_KEY_PASSPHRASE, keyPassphrase, profile)
            prefs.putSecret(SSH_CERTIFICATE_ENCRYPTED, SSH_CERTIFICATE, certificate, profile)
        }
    }

    suspend fun savePorts(peer: Int, web: Int, sshPortStr: String) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            prefs[getProfileKey(SERVER_PEER_PORT, profile)] = peer
            prefs[getProfileKey(SERVER_WEB_PORT, profile)] = web
            prefs[getProfileKey(DEPLOY_SSH_PORT, profile)] = sshPortStr
        }
    }

    suspend fun saveServerPeerPort(peer: Int) {
        if (peer !in 1..65535) return
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(SERVER_PEER_PORT, profile)] = peer
        }
    }

    suspend fun saveDeploy(ip: String) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            prefs[getProfileKey(DEPLOY_IP, profile)] = ip
        }
    }

    suspend fun saveExcludedApps(packages: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(EXCLUDED_APPS, profile)] = packages
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveWhitelistApps(packages: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(WHITELIST_APPS, profile)] = packages
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveAppRoutingLists(blacklist: String, whitelist: String, isWhitelist: Boolean? = null) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(EXCLUDED_APPS, profile)] = blacklist
            prefs[getProfileKey(WHITELIST_APPS, profile)] = whitelist
            if (isWhitelist != null) prefs[getProfileKey(IS_WHITELIST, profile)] = isWhitelist
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveConnectionPassword(password: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
            val legacyKey = getProfileKey(CONNECTION_PASSWORD, profile)
            val encryptedKey = getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)
            if (password.isBlank()) {
                prefs.remove(localKey)
            } else {
                prefs[localKey] = password
            }
            prefs.remove(legacyKey)
            prefs.remove(encryptedKey)
        }
    }

    suspend fun resetUnreadableConnectionPassword(): Boolean {
        var reset = false
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
            val legacyKey = getProfileKey(CONNECTION_PASSWORD, profile)
            val encryptedKey = getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)
            if (
                shouldResetUnreadableTunnelPassword(
                    prefs[localKey],
                    prefs[legacyKey],
                    prefs[encryptedKey],
                    secureStore::decrypt,
                )
            ) {
                prefs.remove(localKey)
                prefs.remove(legacyKey)
                prefs.remove(encryptedKey)
                reset = true
            }
        }
        return reset
    }

    suspend fun saveDeploySecrets(mainPass: String, sshLogin: String, sshPass: String, webLogin: String, webPass: String) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            prefs.putSecret(DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, mainPass, profile)
            prefs[getProfileKey(DEPLOY_LOGIN, profile)] = sshLogin
            prefs.putSecret(DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_PASSWORD, sshPass, profile)
            prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] = webLogin
            prefs.putSecret(DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, webPass, profile)
        }
    }

    suspend fun saveVkAuthMode(mode: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val normalized = when (mode) {
                CsqttConstants.VkAuth.MODE_CAPTCHA -> CsqttConstants.VkAuth.MODE_CAPTCHA
                CsqttConstants.VkAuth.MODE_AUTO_JS -> CsqttConstants.VkAuth.MODE_AUTO_JS
                else -> CsqttConstants.VkAuth.MODE_CALLS
            }
            prefs[getProfileKey(VK_AUTH_MODE, profile)] = normalized
            prefs[getProfileKey(VK_HASH_MODE, profile)] = vkHashModeForAuthMode(
                requestedAuthMode = normalized,
                currentHashMode = prefs[getProfileKey(VK_HASH_MODE, profile)],
                hasVkToken = readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, profile).isNotBlank(),
            )
        }
    }

    suspend fun saveObfsMode(mode: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(OBFS_MODE, profile)] = mode
        }
    }

    suspend fun saveTurnTransport(transport: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(TURN_TRANSPORT, profile)] = when (transport) {
                CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS -> CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
                else -> CsqttConstants.Tunnel.DEFAULT_TURN_TRANSPORT
            }
        }
    }

    suspend fun saveSrtpMode(mode: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(SRTP_MODE, profile)] = when (mode) {
                CsqttConstants.SrtpMode.WRAP -> CsqttConstants.SrtpMode.WRAP
                CsqttConstants.SrtpMode.DTLS -> CsqttConstants.SrtpMode.DTLS
                else -> CsqttConstants.SrtpMode.DEFAULT
            }
        }
    }

    suspend fun saveSelectedTurnSubnet(subnetId: String, ipLimit: String? = null) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val limit = ipLimit ?: prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] ?: "ALL"
            val allIps = if (subnetId.isNotBlank()) TurnSubnetRegistry.getIpsForSubnet(subnetId) else emptyList()
            val limitCount = limit.toIntOrNull()
            val ips = if (allIps.isEmpty()) {
                ""
            } else if (limitCount != null && limitCount > 0) {
                allIps.take(limitCount).joinToString(",")
            } else {
                allIps.joinToString(",")
            }
            prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] = subnetId
            prefs[getProfileKey(CUSTOM_TURN_IPS, profile)] = ips
            if (ipLimit != null) {
                prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] = ipLimit
            }
        }
    }

    suspend fun saveTurnSubnetIpLimit(limit: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val subnetId = prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] ?: ""
            val allIps = if (subnetId.isNotBlank()) TurnSubnetRegistry.getIpsForSubnet(subnetId) else emptyList()
            val limitCount = limit.toIntOrNull()
            val ips = if (allIps.isEmpty()) {
                ""
            } else if (limitCount != null && limitCount > 0) {
                allIps.take(limitCount).joinToString(",")
            } else {
                allIps.joinToString(",")
            }
            prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] = limit
            prefs[getProfileKey(CUSTOM_TURN_IPS, profile)] = ips
        }
    }

    suspend fun saveExtraWorkers(enabled: Boolean = false) {
        dataStore.edit { prefs ->
            for (p in CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX) {
                prefs[getProfileKey(EXTRA_WORKERS, p)] = false
            }
        }
    }

    suspend fun saveAutoJsRiskAcknowledged(acknowledged: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_JS_RISK_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun saveTcpTransportRiskAcknowledged(acknowledged: Boolean) {
        dataStore.edit { prefs ->
            prefs[TCP_TRANSPORT_RISK_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun saveAutoPauseOnWifi(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_PAUSE_ON_WIFI] = enabled
        }
    }

    suspend fun markBatteryOptimizationPromptHandled() {
        dataStore.edit { prefs ->
            prefs[BATTERY_OPTIMIZATION_PROMPT_HANDLED] = true
        }
    }

    suspend fun saveCaptchaMode(mode: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CAPTCHA_MODE, profile)] = mode
        }
    }

    suspend fun saveCaptchaSolveMethod(method: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CAPTCHA_SOLVE_METHOD, profile)] = method
        }
    }

    suspend fun saveWbvCaptchaSolveMethod(method: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CAPTCHA_WBV_SOLVE_METHOD, profile)] = method
            if (prefs[getProfileKey(CAPTCHA_MODE, profile)] == "wv") {
                prefs[getProfileKey(CAPTCHA_SOLVE_METHOD, profile)] = method
            }
        }
    }

    suspend fun saveVkHashMode(mode: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val currentAuthMode = prefs[getProfileKey(VK_AUTH_MODE, profile)]
            val nextAuthMode = vkAuthModeForHashMode(mode, currentAuthMode)
            val selection = normalizeVkModeSelection(
                requestedAuthMode = nextAuthMode,
                requestedHashMode = mode,
                hasVkToken = readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, profile).isNotBlank(),
            )
            prefs[getProfileKey(VK_AUTH_MODE, profile)] = selection.authMode
            prefs[getProfileKey(VK_HASH_MODE, profile)] = selection.hashMode
        }
    }

    suspend fun saveVkAccessToken(token: String, userId: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs.putSecret(VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, token, profile)
            prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile)] = userId
        }
    }

    suspend fun clearVkAccessToken() {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs.remove(getProfileKey(VK_ACCESS_TOKEN_ENCRYPTED, profile))
            prefs.remove(getProfileKey(VK_ACCESS_TOKEN, profile))
            prefs.remove(getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile))
        }
    }

    suspend fun saveIsWhitelist(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(IS_WHITELIST, profile)] = enabled
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveExceptionsMode(packages: String, isWhitelist: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            if (isWhitelist) {
                prefs[getProfileKey(WHITELIST_APPS, profile)] = packages
            } else {
                prefs[getProfileKey(EXCLUDED_APPS, profile)] = packages
            }
            prefs[getProfileKey(IS_WHITELIST, profile)] = isWhitelist
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveDomainRoutingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DOMAIN_ROUTING_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveExcludeRuZonesEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveTemplateDomainsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(TEMPLATE_DOMAINS_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveDomainIsWhitelist(isWhitelist: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DOMAIN_IS_WHITELIST, profile)] = isWhitelist
        }
    }

    suspend fun saveDirectDomains(domains: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_DOMAINS, profile)] = domains
            prefs[getProfileKey(DIRECT_DOMAINS_BLACK, profile)] = domains
        }
    }

    suspend fun saveDirectDomainsBlack(domains: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_DOMAINS_BLACK, profile)] = domains
            prefs[getProfileKey(DIRECT_DOMAINS, profile)] = domains
        }
    }

    suspend fun saveDirectDomainsWhite(domains: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_DOMAINS_WHITE, profile)] = domains
        }
    }

    suspend fun saveIpRoutingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(IP_ROUTING_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveIpIsWhitelist(isWhitelist: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(IP_IS_WHITELIST, profile)] = isWhitelist
        }
    }

    suspend fun saveDirectIpsBlack(ips: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_IPS_BLACK, profile)] = ips
        }
    }

    suspend fun saveDirectIpsWhite(ips: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_IPS_WHITE, profile)] = ips
        }
    }

    private suspend fun migrateSecretsToKeystore() {
        dataStore.edit { prefs ->
            for (profile in CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX) {
                prefs.migrateSecret(getProfileKey(DEPLOY_PASSWORD_ENCRYPTED, profile), getProfileKey(DEPLOY_PASSWORD, profile))
                prefs.migrateSecret(getProfileKey(DEPLOY_MAIN_PASSWORD_ENCRYPTED, profile), getProfileKey(DEPLOY_MAIN_PASSWORD, profile))
                prefs.migrateSecret(getProfileKey(DEPLOY_WEB_PASSWORD_ENCRYPTED, profile), getProfileKey(DEPLOY_WEB_PASSWORD, profile))
            }
        }
    }

    private suspend fun migrateConnectionPasswordsToLocalStorage() {
        dataStore.edit { prefs ->
            for (profile in CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX) {
                val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
                val legacyKey = getProfileKey(CONNECTION_PASSWORD, profile)
                val encryptedKey = getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)
                val password = resolveTunnelPasswordStorage(
                    prefs[localKey],
                    prefs[legacyKey],
                    prefs[encryptedKey],
                    secureStore::decrypt,
                )
                if (password.state == StoredSecretState.Readable) {
                    if (password.value.isBlank()) {
                        prefs.remove(localKey)
                    } else {
                        prefs[localKey] = password.value
                    }
                    prefs.remove(legacyKey)
                    prefs.remove(encryptedKey)
                }
            }
        }
    }

    suspend fun migrateLegacyWhitelistMode() {
        val currentPrefs = dataStore.data.first()
        if (currentPrefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] == true) return

        val hasLegacyWhitelist =
            (CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX).any { profile ->
            currentPrefs[getProfileKey(IS_WHITELIST, profile)] == true
        }
        val installedApps = if (hasLegacyWhitelist) installedSplitTunnelPackages() else emptySet()
        dataStore.edit { prefs ->
            if (prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] == true) return@edit

            for (profile in CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX) {
                val whitelistKey = getProfileKey(IS_WHITELIST, profile)
                val appsKey = getProfileKey(EXCLUDED_APPS, profile)
                val whitelistAppsKey = getProfileKey(WHITELIST_APPS, profile)
                if (prefs[whitelistAppsKey] == null && prefs[whitelistKey] == true) {
                    val existing = prefs[appsKey]
                    if (!existing.isNullOrEmpty()) {
                        prefs[whitelistAppsKey] = existing
                    }
                }
                if (prefs[whitelistKey] == true) {
                    val legacyExcluded = prefs[appsKey]
                        ?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.toSet()
                        ?: emptySet()
                    prefs[appsKey] = (installedApps - legacyExcluded).sorted().joinToString(",")
                }
            }

            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    private fun installedSplitTunnelPackages(): Set<String> {
        return runCatching {
            appContext.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }
                .filter {
                    it != appContext.packageName &&
                        !it.contains("vkontakte") &&
                        !it.contains("vk.calls")
                }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun readSecret(
        prefs: Preferences,
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        profile: Int
    ): String = readSecretState(prefs, encryptedKey, legacyKey, profile).value

    private fun readSecretState(
        prefs: Preferences,
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        profile: Int
    ): StoredSecret {
        val profEncryptedKey = getProfileKey(encryptedKey, profile)
        val profLegacyKey = getProfileKey(legacyKey, profile)
        return resolveStoredSecret(
            prefs[profEncryptedKey],
            prefs[profLegacyKey],
            secureStore::decrypt,
        )
    }

    private fun readConnectionPasswordState(
        prefs: Preferences,
        profile: Int,
    ): StoredSecret = resolveTunnelPasswordStorage(
        prefs[getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)],
        prefs[getProfileKey(CONNECTION_PASSWORD, profile)],
        prefs[getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)],
        secureStore::decrypt,
    )

    private fun MutablePreferences.putSecret(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        value: String,
        profile: Int
    ) {
        val profEncryptedKey = getProfileKey(encryptedKey, profile)
        val profLegacyKey = getProfileKey(legacyKey, profile)
        if (value.isBlank()) {
            remove(profEncryptedKey)
            remove(profLegacyKey)
        } else {
            this[profEncryptedKey] = secureStore.encrypt(value)
            remove(profLegacyKey)
        }
    }

    private fun MutablePreferences.migrateSecret(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>
    ) {
        val legacyValue = this[legacyKey]
        val encryptedValue = this[encryptedKey]
        if (!encryptedValue.isNullOrBlank()) {
            remove(legacyKey)
            return
        }
        if (!legacyValue.isNullOrBlank()) {
            runCatching {
                this[encryptedKey] = secureStore.encrypt(legacyValue)
                remove(legacyKey)
            }
        }
    }

    suspend fun exportConfigJson(
        includeTunnel: Boolean,
        includeDeploy: Boolean,
        includeExceptions: Boolean,
        includeSettings: Boolean
    ): String {
        val prefs = dataStore.data.first()
        // the ACTIVE profile's values (base keys are profile 0's), and secrets the way the store keeps them
        val p = prefs[ACTIVE_PROFILE] ?: 0
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())

        if (includeTunnel) {
            val tunnel = JSONObject()
            tunnel.put("peer", prefs[getProfileKey(PEER, p)] ?: "")
            tunnel.put("listen_port", prefs[getProfileKey(LISTEN_PORT, p)] ?: CsqttConstants.Network.DEFAULT_LOCAL_PORT)
            tunnel.put("manual_ports_enabled", prefs[getProfileKey(MANUAL_PORTS_ENABLED, p)] ?: false)
            tunnel.put("server_peer_port", prefs[getProfileKey(SERVER_PEER_PORT, p)] ?: CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT)
            tunnel.put("connection_password", readConnectionPasswordState(prefs, p).value)
            tunnel.put("obfs_mode", prefs[getProfileKey(OBFS_MODE, p)] ?: CsqttConstants.Tunnel.DEFAULT_OBFS_MODE)
            tunnel.put("turn_transport", prefs[getProfileKey(TURN_TRANSPORT, p)] ?: CsqttConstants.Tunnel.DEFAULT_TURN_TRANSPORT)
            tunnel.put("srtp_mode", prefs[getProfileKey(SRTP_MODE, p)] ?: CsqttConstants.SrtpMode.DEFAULT)
            tunnel.put("custom_turn_ips", prefs[getProfileKey(CUSTOM_TURN_IPS, p)] ?: "")
            tunnel.put("sni", prefs[getProfileKey(SNI, p)] ?: "")
            tunnel.put("no_dns", prefs[getProfileKey(NO_DNS, p)] ?: false)
            root.put("tunnel", tunnel)
        }

        if (includeDeploy) {
            val deploy = JSONObject()
            deploy.put("deploy_ip", prefs[getProfileKey(DEPLOY_IP, p)] ?: "")
            deploy.put("deploy_login", prefs[getProfileKey(DEPLOY_LOGIN, p)] ?: "")
            deploy.put("deploy_ssh_port", prefs[getProfileKey(DEPLOY_SSH_PORT, p)] ?: "")
            deploy.put("server_web_port", prefs[getProfileKey(SERVER_WEB_PORT, p)] ?: CsqttConstants.Network.DEFAULT_SERVER_WEB_PORT)
            deploy.put("docker_install", prefs[getProfileKey(DOCKER_INSTALL, p)] ?: true)
            deploy.put("deploy_instance", prefs[getProfileKey(DEPLOY_INSTANCE, p)] ?: "side")
            deploy.put("ssh_keys_mode", prefs[getProfileKey(SSH_KEYS_MODE, p)] ?: false)
            deploy.put("vk_hashes", prefs[getProfileKey(VK_HASHES, p)] ?: "")
            deploy.put("secondary_vk_hash", prefs[getProfileKey(SECONDARY_VK_HASH, p)] ?: "")
            deploy.put("vk_auth_mode", prefs[getProfileKey(VK_AUTH_MODE, p)] ?: CsqttConstants.VkAuth.MODE_CALLS)
            deploy.put("vk_hash_mode", prefs[getProfileKey(VK_HASH_MODE, p)] ?: CsqttConstants.VkAutoHash.MODE_MANUAL)
            deploy.put("vk_access_token", readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, p))
            deploy.put("workers_per_hash", prefs[getProfileKey(WORKERS_PER_HASH, p)] ?: 18)
            deploy.put("extra_workers", prefs[getProfileKey(EXTRA_WORKERS, p)] ?: false)
            deploy.put("csqtt_link", prefs[getProfileKey(CSQTT_LINK, p)] ?: "")
            deploy.put("csqtt_link_mode", prefs[getProfileKey(CSQTT_LINK_MODE, p)] ?: false)
            deploy.put("secret_path_enabled", prefs[getProfileKey(DEPLOY_SECRET_PATH_ENABLED, p)] ?: false)
            deploy.put("secret_path_uuid", prefs[getProfileKey(DEPLOY_SECRET_PATH_UUID, p)] ?: "")
            root.put("deploy", deploy)
        }

        if (includeExceptions) {
            val exc = JSONObject()
            exc.put("excluded_apps", prefs[getProfileKey(EXCLUDED_APPS, p)] ?: "")
            exc.put("whitelist_apps", prefs[getProfileKey(WHITELIST_APPS, p)] ?: "")
            exc.put("is_whitelist", prefs[getProfileKey(IS_WHITELIST, p)] ?: false)
            exc.put("domain_routing_enabled", prefs[getProfileKey(DOMAIN_ROUTING_ENABLED, p)] ?: false)
            exc.put("domain_is_whitelist", prefs[getProfileKey(DOMAIN_IS_WHITELIST, p)] ?: false)
            exc.put("exclude_ru_zones_enabled", prefs[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, p)] ?: false)
            exc.put("template_domains_enabled", prefs[getProfileKey(TEMPLATE_DOMAINS_ENABLED, p)] ?: false)
            exc.put("direct_domains_black", prefs[getProfileKey(DIRECT_DOMAINS_BLACK, p)] ?: prefs[getProfileKey(DIRECT_DOMAINS, p)] ?: "")
            exc.put("direct_domains_white", prefs[getProfileKey(DIRECT_DOMAINS_WHITE, p)] ?: "")
            exc.put("ip_routing_enabled", prefs[getProfileKey(IP_ROUTING_ENABLED, p)] ?: false)
            exc.put("ip_is_whitelist", prefs[getProfileKey(IP_IS_WHITELIST, p)] ?: false)
            exc.put("direct_ips_black", prefs[getProfileKey(DIRECT_IPS_BLACK, p)] ?: "")
            exc.put("direct_ips_white", prefs[getProfileKey(DIRECT_IPS_WHITE, p)] ?: "")
            root.put("exceptions", exc)
        }

        if (includeSettings) {
            val settings = JSONObject()
            settings.put("theme_mode", prefs[THEME_MODE] ?: "system")
            settings.put("is_dynamic_color", prefs[IS_DYNAMIC_COLOR] ?: false)
            settings.put("theme_palette", prefs[THEME_PALETTE] ?: "pure_black")
            settings.put("auto_pause_on_wifi", prefs[AUTO_PAUSE_ON_WIFI] ?: false)
            settings.put("selected_fingerprint", prefs[getProfileKey(SELECTED_FINGERPRINT, p)] ?: "chrome")
            settings.put("selected_turn_subnet", prefs[getProfileKey(SELECTED_TURN_SUBNET, p)] ?: "")
            settings.put("turn_subnet_ip_limit", prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, p)] ?: "ALL")
            settings.put("logging_enabled", prefs[LOGGING_ENABLED] ?: true)
            settings.put("detailed_logs", prefs[DETAILED_LOGS] ?: false)
            settings.put("participant_show_logs", prefs[getProfileKey(PARTICIPANT_SHOW_LOGS, p)] ?: false)
            root.put("settings", settings)
        }

        return root.toString(2)
    }

    suspend fun importConfigJson(
        jsonString: String,
        includeTunnel: Boolean,
        includeDeploy: Boolean,
        includeExceptions: Boolean,
        includeSettings: Boolean
    ): Boolean {
        return runCatching {
            val root = JSONObject(jsonString)
            dataStore.edit { mutable ->
                // into the ACTIVE profile (base keys are profile 0's), secrets the way the store keeps them
                val p = mutable[ACTIVE_PROFILE] ?: 0
                if (includeTunnel && root.has("tunnel")) {
                    val tunnel = root.getJSONObject("tunnel")
                    if (tunnel.has("peer")) mutable[getProfileKey(PEER, p)] = tunnel.getString("peer")
                    if (tunnel.has("listen_port")) mutable[getProfileKey(LISTEN_PORT, p)] = tunnel.getInt("listen_port")
                    if (tunnel.has("manual_ports_enabled")) mutable[getProfileKey(MANUAL_PORTS_ENABLED, p)] = tunnel.getBoolean("manual_ports_enabled")
                    if (tunnel.has("server_peer_port")) mutable[getProfileKey(SERVER_PEER_PORT, p)] = tunnel.getInt("server_peer_port")
                    if (tunnel.has("connection_password")) {
                        val pw = tunnel.getString("connection_password")
                        // as saveConnectionPassword stores it
                        if (pw.isBlank()) mutable.remove(getProfileKey(CONNECTION_PASSWORD_LOCAL, p))
                        else mutable[getProfileKey(CONNECTION_PASSWORD_LOCAL, p)] = pw
                        mutable.remove(getProfileKey(CONNECTION_PASSWORD, p))
                        mutable.remove(getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, p))
                    }
                    if (tunnel.has("obfs_mode")) mutable[getProfileKey(OBFS_MODE, p)] = tunnel.getString("obfs_mode")
                    if (tunnel.has("turn_transport")) mutable[getProfileKey(TURN_TRANSPORT, p)] = tunnel.getString("turn_transport")
                    if (tunnel.has("srtp_mode")) mutable[getProfileKey(SRTP_MODE, p)] = tunnel.getString("srtp_mode")
                    if (tunnel.has("custom_turn_ips")) mutable[getProfileKey(CUSTOM_TURN_IPS, p)] = tunnel.getString("custom_turn_ips")
                    if (tunnel.has("sni")) mutable[getProfileKey(SNI, p)] = tunnel.getString("sni")
                    if (tunnel.has("no_dns")) mutable[getProfileKey(NO_DNS, p)] = tunnel.getBoolean("no_dns")
                }
                if (includeDeploy && root.has("deploy")) {
                    val deploy = root.getJSONObject("deploy")
                    if (deploy.has("deploy_ip")) mutable[getProfileKey(DEPLOY_IP, p)] = deploy.getString("deploy_ip")
                    if (deploy.has("deploy_login")) mutable[getProfileKey(DEPLOY_LOGIN, p)] = deploy.getString("deploy_login")
                    if (deploy.has("deploy_ssh_port")) mutable[getProfileKey(DEPLOY_SSH_PORT, p)] = deploy.getString("deploy_ssh_port")
                    if (deploy.has("server_web_port")) mutable[getProfileKey(SERVER_WEB_PORT, p)] = deploy.getInt("server_web_port")
                    if (deploy.has("docker_install")) mutable[getProfileKey(DOCKER_INSTALL, p)] = deploy.getBoolean("docker_install")
                    if (deploy.has("deploy_instance")) mutable[getProfileKey(DEPLOY_INSTANCE, p)] = deploy.getString("deploy_instance")
                    if (deploy.has("ssh_keys_mode")) mutable[getProfileKey(SSH_KEYS_MODE, p)] = deploy.getBoolean("ssh_keys_mode")
                    if (deploy.has("vk_hashes")) mutable[getProfileKey(VK_HASHES, p)] = deploy.getString("vk_hashes")
                    if (deploy.has("secondary_vk_hash")) mutable[getProfileKey(SECONDARY_VK_HASH, p)] = deploy.getString("secondary_vk_hash")
                    if (deploy.has("vk_auth_mode")) mutable[getProfileKey(VK_AUTH_MODE, p)] = deploy.getString("vk_auth_mode")
                    if (deploy.has("vk_hash_mode")) mutable[getProfileKey(VK_HASH_MODE, p)] = deploy.getString("vk_hash_mode")
                    if (deploy.has("vk_access_token")) mutable.putSecret(VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, deploy.getString("vk_access_token"), p)
                    if (deploy.has("workers_per_hash")) mutable[getProfileKey(WORKERS_PER_HASH, p)] = deploy.getInt("workers_per_hash")
                    if (deploy.has("extra_workers")) mutable[getProfileKey(EXTRA_WORKERS, p)] = deploy.getBoolean("extra_workers")
                    if (deploy.has("csqtt_link")) mutable[getProfileKey(CSQTT_LINK, p)] = deploy.getString("csqtt_link")
                    if (deploy.has("csqtt_link_mode")) mutable[getProfileKey(CSQTT_LINK_MODE, p)] = deploy.getBoolean("csqtt_link_mode")
                    if (deploy.has("secret_path_enabled")) mutable[getProfileKey(DEPLOY_SECRET_PATH_ENABLED, p)] = deploy.getBoolean("secret_path_enabled")
                    if (deploy.has("secret_path_uuid")) mutable[getProfileKey(DEPLOY_SECRET_PATH_UUID, p)] = deploy.getString("secret_path_uuid")
                }
                if (includeExceptions && root.has("exceptions")) {
                    val exc = root.getJSONObject("exceptions")
                    if (exc.has("excluded_apps")) mutable[getProfileKey(EXCLUDED_APPS, p)] = exc.getString("excluded_apps")
                    if (exc.has("whitelist_apps")) mutable[getProfileKey(WHITELIST_APPS, p)] = exc.getString("whitelist_apps")
                    if (exc.has("is_whitelist")) mutable[getProfileKey(IS_WHITELIST, p)] = exc.getBoolean("is_whitelist")
                    if (exc.has("domain_routing_enabled")) mutable[getProfileKey(DOMAIN_ROUTING_ENABLED, p)] = exc.getBoolean("domain_routing_enabled")
                    if (exc.has("domain_is_whitelist")) mutable[getProfileKey(DOMAIN_IS_WHITELIST, p)] = exc.getBoolean("domain_is_whitelist")
                    if (exc.has("exclude_ru_zones_enabled")) mutable[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, p)] = exc.getBoolean("exclude_ru_zones_enabled")
                    if (exc.has("template_domains_enabled")) mutable[getProfileKey(TEMPLATE_DOMAINS_ENABLED, p)] = exc.getBoolean("template_domains_enabled")
                    if (exc.has("direct_domains_black")) mutable[getProfileKey(DIRECT_DOMAINS_BLACK, p)] = exc.getString("direct_domains_black")
                    if (exc.has("direct_domains_white")) mutable[getProfileKey(DIRECT_DOMAINS_WHITE, p)] = exc.getString("direct_domains_white")
                    if (exc.has("ip_routing_enabled")) mutable[getProfileKey(IP_ROUTING_ENABLED, p)] = exc.getBoolean("ip_routing_enabled")
                    if (exc.has("ip_is_whitelist")) mutable[getProfileKey(IP_IS_WHITELIST, p)] = exc.getBoolean("ip_is_whitelist")
                    if (exc.has("direct_ips_black")) mutable[getProfileKey(DIRECT_IPS_BLACK, p)] = exc.getString("direct_ips_black")
                    if (exc.has("direct_ips_white")) mutable[getProfileKey(DIRECT_IPS_WHITE, p)] = exc.getString("direct_ips_white")
                }
                if (includeSettings && root.has("settings")) {
                    val s = root.getJSONObject("settings")
                    if (s.has("theme_mode")) mutable[THEME_MODE] = s.getString("theme_mode")
                    if (s.has("is_dynamic_color")) mutable[IS_DYNAMIC_COLOR] = s.getBoolean("is_dynamic_color")
                    if (s.has("theme_palette")) mutable[THEME_PALETTE] = s.getString("theme_palette")
                    if (s.has("auto_pause_on_wifi")) mutable[AUTO_PAUSE_ON_WIFI] = s.getBoolean("auto_pause_on_wifi")
                    if (s.has("selected_fingerprint")) mutable[getProfileKey(SELECTED_FINGERPRINT, p)] = s.getString("selected_fingerprint")
                    if (s.has("selected_turn_subnet")) mutable[getProfileKey(SELECTED_TURN_SUBNET, p)] = s.getString("selected_turn_subnet")
                    if (s.has("turn_subnet_ip_limit")) mutable[getProfileKey(TURN_SUBNET_IP_LIMIT, p)] = s.getString("turn_subnet_ip_limit")
                    if (s.has("logging_enabled")) mutable[LOGGING_ENABLED] = s.getBoolean("logging_enabled")
                    if (s.has("detailed_logs")) mutable[DETAILED_LOGS] = s.getBoolean("detailed_logs")
                    val currentProfile = mutable[ACTIVE_PROFILE] ?: 0
                    if (s.has("participant_show_logs")) mutable[getProfileKey(PARTICIPANT_SHOW_LOGS, currentProfile)] = s.getBoolean("participant_show_logs")
                }
            }
            true
        }.getOrDefault(false)
    }
}
