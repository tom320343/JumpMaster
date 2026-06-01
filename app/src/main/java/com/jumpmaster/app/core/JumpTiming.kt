package com.jumpmaster.app.core

import kotlin.math.sqrt

object JumpTiming {
    fun calculatePressTimeMs(
        distance: Float,
        deltaPieceY: Int,
        pressCoefficient: Float,
        headDiameter: Float
    ): Long {
        val diameter = headDiameter.coerceAtLeast(1f).toDouble()
        val scale = 0.945 * 2.0 / diameter
        val actualDistance = distance.toDouble() * scale * (sqrt(6.0) / 2.0)
        val rawPressTime = (-945.0 + sqrt(945.0 * 945.0 + 4.0 * 105.0 * 36.0 * actualDistance)) /
                (2.0 * 105.0) * 1000.0
        val duration = rawPressTime * pressCoefficient + deltaPieceY
        return duration.toLong().coerceIn(200L, 5000L)
    }
}
