package com.example.bleestimation

import java.util.LinkedList
import java.util.Queue

/**
 * 移動平均をとるためのクラス
 * - windowSize分の (x,y) を保持して平均を返す
 */
class MovingAverage(private val windowSize: Int) {

    private val queue: Queue<Pair<Double, Double>> = LinkedList()

    fun addAndGetAverage(newPos: Pair<Double, Double>): Pair<Double, Double> {
        queue.add(newPos)
        if (queue.size > windowSize) {
            queue.remove()
        }

        var sumX = 0.0
        var sumY = 0.0
        for (p in queue) {
            sumX += p.first
            sumY += p.second
        }
        val n = queue.size.toDouble()
        return Pair(sumX / n, sumY / n)
    }

    /**
     * 直接現在の平均だけ取得する場合
     */
    fun getCurrentAverage(): Pair<Double, Double>? {
        if (queue.isEmpty()) return null
        var sumX = 0.0
        var sumY = 0.0
        for (p in queue) {
            sumX += p.first
            sumY += p.second
        }
        val n = queue.size.toDouble()
        return Pair(sumX / n, sumY / n)
    }

    fun clear() {
        queue.clear()
    }
}
