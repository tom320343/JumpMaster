package com.jumpmaster.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceConfigTest {

    @Test
    fun `local density override does not shrink head diameter below screen-width calibration`() {
        val config = DeviceConfig.forScreen(width = 1080, height = 2376, densityDpi = 480)

        assertEquals(80f, config.headDiameter, 0.01f)
    }
}
