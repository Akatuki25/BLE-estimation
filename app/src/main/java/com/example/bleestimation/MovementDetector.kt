package com.example.bleestimation

import kotlin.math.hypot

/**
 * 連続する位置推定値を見て、「移動しているか」を判定するクラス
 * - 現在位置と前回位置の距離が threshold を超えると「移動中」とみなす
 */
class MovementDetector(private val threshold: Double = 0.2) {

    fun isMoving(
        currentPos: Pair<Double, Double>?,
        lastPos: Pair<Double, Double>?
    ): Boolean {
        if (currentPos == null || lastPos == null) return false

        val dx = currentPos.first - lastPos.first
        val dy = currentPos.second - lastPos.second
        val dist = hypot(dx, dy)
        return dist > threshold
    }
}
