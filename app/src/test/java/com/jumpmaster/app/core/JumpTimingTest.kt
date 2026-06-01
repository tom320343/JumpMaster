package com.jumpmaster.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class JumpTimingTest {

    @Test
    fun `wechat timing matches the original nonlinear model`() {
        val distance = 620f

        val duration = JumpTiming.calculatePressTimeMs(
            distance = distance,
            deltaPieceY = 0,
            pressCoefficient = 1.392f,
            headDiameter = 70f
        )

        assertEquals(1006L, duration)
    }

    @Test
    fun `wechat timing keeps minimum tap duration`() {
        val duration = JumpTiming.calculatePressTimeMs(
            distance = 1f,
            deltaPieceY = 0,
            pressCoefficient = 1.392f,
            headDiameter = 70f
        )

        assertEquals(200L, duration)
    }

    @Test
    fun `head diameter follows original pc adb density calibration`() {
        val withPhysicalDensity640 = JumpTiming.calculatePressTimeMs(
            distance = 620f,
            deltaPieceY = 0,
            pressCoefficient = 1.392f,
            headDiameter = 80f
        )

        assertEquals(888L, withPhysicalDensity640)
    }
}
