package com.csqtt.client

import com.csqtt.client.ui.legacy.utils.parseCsqttLink
import com.csqtt.client.ui.legacy.utils.peerAddress
import kotlinx.coroutines.flow.first

data class ConnectionSource(
    val peer: String,
    val password: String,
    val hashes: String,
    val hashesFromLink: Boolean,
    val vkAccessToken: String,
    val tokenFromLink: Boolean,
)

/** A participant's hashes: the owner's from the link minus the ones taken out, then the participant's own. */
internal fun participantHashes(linkHashes: List<String>, removed: Set<String>, ownHashes: String): String =
    (linkHashes.filterNot { it in removed } + ownHashes.split(Regex("[,\\s\\n]+")))
        .map(String::trim).filter(String::isNotEmpty).distinct().joinToString(",")

suspend fun resolveConnectionSource(store: SettingsStore): ConnectionSource? {
    val invalidHashes = VkHashValidationCodec.decode(store.vkHashCheckResults.first())
    fun activeHashes(raw: String): String {
        val rawList = raw.split(Regex("[,\\s\\n]+")).map(String::trim).filter { it.length >= 16 }.distinct()
        val filtered = VkHashValidationCodec.active(rawList, invalidHashes)
        return (if (filtered.isNotEmpty()) filtered else rawList).joinToString(",")
    }

    if (store.csqttLinkMode.first()) {
        val link = parseCsqttLink(store.csqttLink.first()) ?: return null
        val removed = store.linkHashesRemoved.first()
        val ownerHashes = link.hashes.filterNot { it in removed }
        val tokenFromLink = link.vkAccessToken.isNotEmpty()
        return ConnectionSource(
            peer = link.peerAddress(),
            password = link.password,
            hashes = if (tokenFromLink) "" else activeHashes(participantHashes(link.hashes, removed, store.vkHashes.first())),
            hashesFromLink = ownerHashes.isNotEmpty(),
            vkAccessToken = link.vkAccessToken,
            tokenFromLink = tokenFromLink,
        )
    }

    val basePeer = store.peer.first()
    val passwordState = store.connectionPasswordState.first()
    val password = passwordState.value
    if (basePeer.isBlank()) return null
    val serverPeerPort = store.serverPeerPort.first().takeIf { it in 1..65535 }
        ?: CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT
    val peer = if (basePeer.contains(':')) basePeer else "$basePeer:$serverPeerPort"
    return ConnectionSource(
        peer = peer,
        password = password,
        hashes = activeHashes(store.vkHashes.first()),
        hashesFromLink = false,
        vkAccessToken = "",
        tokenFromLink = false,
    )
}
