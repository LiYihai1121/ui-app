package com.qingqi.adskip.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificatePinTest {

    @Test
    fun `accepts canonical sha256 certificate pin`() {
        assertTrue(SettingsViewModel.isValidCertificatePin("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
    }

    @Test
    fun `rejects malformed certificate pins`() {
        assertFalse(SettingsViewModel.isValidCertificatePin("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
        assertFalse(SettingsViewModel.isValidCertificatePin("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB="))
        assertFalse(SettingsViewModel.isValidCertificatePin("sha1/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
    }
}
