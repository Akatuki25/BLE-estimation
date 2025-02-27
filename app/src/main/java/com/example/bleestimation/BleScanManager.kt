package com.example.bleestimation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.pow
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * BLEスキャン関連 (パーミッションチェック, スキャン開始/停止, スキャンコールバック, RSSI→位置推定 など)
 * また、移動平均やカルマンフィルタ、移動検出などを統合し、推定位置を管理する。
 */
class BleScanManager(
    private val context: Context,
    private val largeWindowMA: MovingAverage,      // 窓幅60の移動平均
    private val smallWindowMA: MovingAverage,      // 窓幅10の移動平均(カルマンフィルタ入力用)
    private val kalmanX: AdaptiveKalman,           // x軸用カルマン
    private val kalmanY: AdaptiveKalman,           // y軸用カルマン
    private val movementDetector: MovementDetector // 動いているか検出
) {
    companion object {
        private const val TAG = "BleScanManager"
    }

    // Bluetooth関連
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    // 移動平均(窓幅60)後の推定座標
    var estimatedPosMA60 by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    // 適応カルマンの推定座標(入力は小さい移動平均(10))
    var estimatedPosKalman by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    // スキャン結果一覧
    private val _scanResults = mutableStateListOf<BleDeviceData>()
    val scanResults: List<BleDeviceData> get() = _scanResults

    // 前回位置(移動検出用)
    private var lastPosition: Pair<Double, Double>? = null

    // 使用するビーコンの定義
    private val knownBeacons = listOf(
        BeaconInfo("MyCustomBeacon1", 2.0, 13.5, -59, 2.0),
        BeaconInfo("MyCustomBeacon2", 0.0, 10.0, -59, 2.0),
        BeaconInfo("MyCustomBeacon3", 2.0, 11.5, -59, 2.0),
        BeaconInfo("MyCustomBeacon4", 0.0, 13.5, -59, 2.0)
    )

    // ========== パーミッション関連 ==========

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        // Android12未満なら位置情報
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        // Android12(S)以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return permissions
    }

    // ========== スキャン開始/停止 ==========

    fun startScan() {
        if (!hasScanPermission()) {
            Log.d(TAG, "No permission")
            return
        }
        Log.d(TAG, "Start scan")
        try {
            _scanResults.clear()
            // スキャンモード設定
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            // フィルタなしで開始
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            Log.d(TAG, "Scan started")

        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start scan", e)
        }
    }

    fun stopScan() {
        if (!hasScanPermission()) return
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // ========== コールバック ==========

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) handleScanResult(r)
        }

        override fun onScanFailed(errorCode: Int) {
            // スキャン失敗時の処理
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val deviceName = result.device.name ?: return
        if (deviceName !in listOf("MyCustomBeacon1", "MyCustomBeacon2", "MyCustomBeacon3", "MyCustomBeacon4")) {
            Log.d(TAG, "handleScanResult: Skipping device with name $deviceName")
            return
        }

        val rssi = result.rssi
        val address = result.device.address

        val index = _scanResults.indexOfFirst { it.address == address }
        if (index >= 0) {
            _scanResults[index] = _scanResults[index].copy(name = deviceName, rssi = rssi)
        } else {
            _scanResults.add(BleDeviceData(deviceName, address, rssi))
        }
        Log.d(TAG, "handleScanResult: Updated scanResults size=${_scanResults.size}")

        // 1. 生の重み付き重心
        val rawPos = estimatePosition(_scanResults)

        // 2. 移動平均(窓幅60)
        val ma60 = rawPos?.let { largeWindowMA.addAndGetAverage(it) }
        estimatedPosMA60 = ma60
        Log.d(TAG, "handleScanResult: estimatedPosMA60=$estimatedPosMA60")

        // 3. 移動平均(窓幅10) → 適応カルマンフィルタ
        val ma10 = rawPos?.let { smallWindowMA.addAndGetAverage(it) }
        if (ma10 != null) {
            val kx = kalmanX.update(ma10.first)
            val ky = kalmanY.update(ma10.second)
            estimatedPosKalman = Pair(kx, ky)
            Log.d(TAG, "handleScanResult: estimatedPosKalman=$estimatedPosKalman")
        }

        // 4. 移動検出
        val isMoving = movementDetector.isMoving(rawPos, lastPosition)
        println("IsMoving: $isMoving, rawPos=$rawPos")
        // 前回位置更新
        lastPosition = rawPos
    }

    // ========== 重み付き重心による位置推定 ==========

    private fun estimatePosition(scanList: List<BleDeviceData>): Pair<Double, Double>? {
        // 対象ビーコンのみ
        val validDevices = scanList.filter { data ->
            knownBeacons.any { it.name == data.name }
        }
        if (validDevices.size < 3) return null

        val beaconDistList = validDevices.mapNotNull { data ->
            val b = knownBeacons.find { it.name == data.name } ?: return@mapNotNull null
            val distance = estimateDistance(data.rssi, b.txPower, b.pathLoss)
            BeaconDistance(b.x, b.y, distance)
        }

        if (beaconDistList.isEmpty()) return null

        var sumWx = 0.0
        var sumWy = 0.0
        var sumW = 0.0
        for (bd in beaconDistList) {
            val dist = if (bd.distance < 0.1) 0.1 else bd.distance
            val w = 1.0 / (dist * dist)
            sumWx += bd.x * w
            sumWy += bd.y * w
            sumW += w
        }

        if (sumW == 0.0) return null
        return Pair(sumWx / sumW, sumWy / sumW)
    }

    private fun estimateDistance(rssi: Int, txPower: Int, pathLoss: Double): Double {
        // 対数距離則
        return 10.0.pow((txPower - rssi) / (10.0 * pathLoss))
    }
}

/** ビーコン情報 */
data class BeaconInfo(
    val name: String,
    val x: Double,
    val y: Double,
    val txPower: Int,
    val pathLoss: Double
)

/** BLEスキャン結果 */
data class BleDeviceData(
    val name: String,
    val address: String,
    val rssi: Int
)

/** ビーコン座標＋推定距離 */
data class BeaconDistance(
    val x: Double,
    val y: Double,
    val distance: Double
)
