package com.acer.batteryinsight.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerVersionTest {

    @Test
    fun `compares versions numerically`() {
        assertTrue(UpdateManager.isNewerVersion("1.3.36", "1.3.37"))
        assertTrue(UpdateManager.isNewerVersion("1.3.9", "1.3.10"))
        assertTrue(UpdateManager.isNewerVersion("1.3", "1.3.1"))
        assertTrue(UpdateManager.isNewerVersion("v1.3.36", "V1.4.0"))
        assertFalse(UpdateManager.isNewerVersion("1.3.36", "1.3.36"))
        assertFalse(UpdateManager.isNewerVersion("1.4.0", "1.3.99"))
        assertFalse(UpdateManager.isNewerVersion("1.3.36", ""))
    }
}
