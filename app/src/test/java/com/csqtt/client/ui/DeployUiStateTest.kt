package com.csqtt.client.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployUiStateTest {
    private val completeState = DeployUiState(
        host = "server.example",
        sshLogin = "root",
        sshPassword = "ssh-password",
        mainPasswordConfigured = true,
        webPanelConfigured = true,
    )

    @Test
    fun `install requires web authorization`() {
        assertTrue(completeState.canInstall)
        assertFalse(completeState.copy(webPanelConfigured = false).canInstall)
    }

    @Test
    fun `install requires main password authorization`() {
        assertTrue(completeState.canInstall)
        assertFalse(completeState.copy(mainPasswordConfigured = false).canInstall)
        assertTrue(isDeployMainPasswordValid("0123456789abcdef"))
        assertFalse(isDeployMainPasswordValid("0123456789abcde"))
        assertFalse(isDeployMainPasswordValid("short"))
        assertFalse(isDeployMainPasswordValid(""))
        assertFalse(isDeployMainPasswordValid("0123456789abcdefg"))
        assertFalse(isDeployMainPasswordValid("0123456789abcde!"))
    }

    @Test
    fun `web login and password are both required`() {
        assertTrue(hasWebPanelCredentials("admin", "secret"))
        assertFalse(hasWebPanelCredentials("", "secret"))
        assertFalse(hasWebPanelCredentials("admin", ""))
        assertFalse(hasWebPanelCredentials("   ", "secret"))
        assertFalse(hasWebPanelCredentials("admin", "   "))
    }

    @Test
    fun `uninstall does not require panel authorization`() {
        assertTrue(
            completeState.copy(
                mainPasswordConfigured = false,
                webPanelConfigured = false,
            ).canUninstall
        )
    }

    @Test
    fun `docker selection preserves deploy validation`() {
        assertTrue(completeState.copy(dockerInstall = true).canInstall)
        assertFalse(completeState.copy(dockerInstall = true, host = "").canInstall)
    }

    @Test
    fun `disabling manual ports restores every deployment port default`() {
        val ports = resolveServerPorts(
            manualPorts = false,
            sshPort = "228",
            peerPort = "47000",
            webPort = "47002",
        )

        assertTrue(ports.ssh == 22)
        assertTrue(ports.peer == 46000)
        assertTrue(ports.web == 46002)
    }

    @Test
    fun `next to 2_1_9 is the default and takes its own ports`() {
        assertTrue(DeployInstance.fromStored(null) == DeployInstance.SIDE)
        assertTrue(DeployInstance.fromStored("garbage") == DeployInstance.SIDE)
        assertTrue(DeployInstance.fromStored("main") == DeployInstance.MAIN)
        assertTrue(DeployUiState().instance == DeployInstance.SIDE)
        val side = resolveServerPorts(manualPorts = false, sshPort = "22", peerPort = "46000", webPort = "46002", instance = DeployInstance.SIDE)
        assertTrue(side.peer == 46100 && side.web == 46102 && side.ssh == 22)
        val main = resolveServerPorts(manualPorts = false, sshPort = "22", peerPort = "1", webPort = "2", instance = DeployInstance.MAIN)
        assertTrue(main.peer == 46000 && main.web == 46002)
        // manual ports are the user's, whatever the mode
        val manual = resolveServerPorts(manualPorts = true, sshPort = "22", peerPort = "47000", webPort = "47002", instance = DeployInstance.SIDE)
        assertTrue(manual.peer == 47000 && manual.web == 47002)
        assertTrue(completeState.copy(instance = DeployInstance.SIDE).panelDisplayUrl.startsWith("https://server.example:46102/"))
        assertTrue(completeState.copy(instance = DeployInstance.MAIN).panelDisplayUrl.startsWith("https://server.example:46002/"))
    }

    @Test
    fun `manual ports preserve valid custom values`() {
        val ports = resolveServerPorts(
            manualPorts = true,
            sshPort = "228",
            peerPort = "47000",
            webPort = "47002",
        )

        assertTrue(ports.ssh == 228)
        assertTrue(ports.peer == 47000)
        assertTrue(ports.web == 47002)
    }

    @Test
    fun `successful deploy synchronizes a nonempty server password`() {
        assertTrue(passwordToSynchronizeAfterDeploy(true, "server-password") == "server-password")
        assertTrue(passwordToSynchronizeAfterDeploy(false, "server-password") == null)
        assertTrue(passwordToSynchronizeAfterDeploy(true, "") == null)
        assertTrue(passwordToSynchronizeAfterDeploy(true, "   ") == null)
    }
}
