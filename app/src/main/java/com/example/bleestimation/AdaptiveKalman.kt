package com.example.bleestimation

/**
 * 1次元の適応カルマンフィルタ(簡易版)
 * - x軸用とy軸用で2つインスタンスを作り、それぞれ独立にフィルタリング
 * - 観測ノイズrをイノベーションに基づき更新
 */
class AdaptiveKalman(
    private var x: Double = 0.0,
    private var p: Double = 1.0,
    private val q: Double = 0.01,  // プロセスノイズ(小さめ)
    private var r: Double = 0.5,   // 観測ノイズ(動的に変化)
    private val alpha: Double = 0.9 // r更新の緩和係数(0.9等)
) {
    private var initialized = false

    fun update(z: Double): Double {
        // 最初は観測値をそのまま採用
        if (!initialized) {
            x = z
            p = 1.0
            initialized = true
            return x
        }

        // 1. 予測ステップ (今回は x固定で pだけ増える)
        val pPredict = p + q

        // 2. 観測更新ステップ
        val k = pPredict / (pPredict + r)
        val innovation = z - x
        x = x + k * innovation
        p = (1 - k) * pPredict

        // 3. 観測ノイズrを適応的に更新
        val residual = innovation * innovation
        r = alpha * r + (1 - alpha) * residual

        return x
    }
}
