package com.example.bleestimation

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * MainActivity はパーミッションの取得と画面セットのみを担当。
 * BLE周りの詳細は BleScanManager へ、UIは MyUI.kt の BLEScannerScreen へ任せる。
 */
class MainActivity : ComponentActivity() {

    // --- 分割したクラスのインスタンスを生成 ---
    private val largeMA = MovingAverage(windowSize = 60)     // サンプル数60の移動平均
    private val smallMA = MovingAverage(windowSize = 10)     // サンプル数10の移動平均(カルマン入力用)
    private val kalmanX = AdaptiveKalman()                   // x軸用 適応カルマン
    private val kalmanY = AdaptiveKalman()                   // y軸用 適応カルマン
    private val movementDetector = MovementDetector(0.2)     // 移動判定用

    private lateinit var bleScanManager: BleScanManager

    // パーミッション要求ランチャ
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value == true }
        if (granted) {
            bleScanManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BleScanManagerを初期化し、必要なクラスを注入
        bleScanManager = BleScanManager(
            context = this,
            largeWindowMA = largeMA,
            smallWindowMA = smallMA,
            kalmanX = kalmanX,
            kalmanY = kalmanY,
            movementDetector = movementDetector
        )

        setContent {
            // 分割したUI(MyUI.kt)のComposableを表示
            BLEScannerScreen(
                bleScanManager = bleScanManager,
                onStartScan = { checkPermissionsAndStartScan() },
                onStopScan = { bleScanManager.stopScan() }
            )
        }
    }

    /**
     * スキャン開始前のパーミッションチェック
     */
    private fun checkPermissionsAndStartScan() {
        val requiredPermissions = bleScanManager.getRequiredPermissions()
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            Log.d("MainActivity", "Requesting permissions: $notGranted")
            permissionsLauncher.launch(notGranted.toTypedArray())
        } else {
            Log.d("MainActivity", "All permissions granted, starting scan.")
            bleScanManager.startScan()
        }
    }
}
