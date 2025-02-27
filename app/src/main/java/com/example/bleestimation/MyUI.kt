package com.example.bleestimation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Jetpack Composeで画面表示を行うファイル。
 * BLEScannerScreen はボタンやテキスト、そして位置可視化用のCanvasを含む。
 */

@Composable
fun BLEScannerScreen(
    bleScanManager: BleScanManager,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // スキャン開始／停止ボタン
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onStartScan, modifier = Modifier.weight(1f)) {
                Text(text = "Start Scan")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onStopScan, modifier = Modifier.weight(1f)) {
                Text(text = "Stop Scan")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 移動平均(60)の結果
        Text(text = "MA(60) GREEN: ${formatPos(bleScanManager.estimatedPosMA60)}")
        // 適応カルマンの結果(入力はMA(10))
        Text(text = "Kalman(MA(10)->AdaptiveKalman) BLUE: ${formatPos(bleScanManager.estimatedPosKalman)}")

        Spacer(modifier = Modifier.height(16.dp))

        // 簡易可視化
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.LightGray),
            contentAlignment = Alignment.TopStart
        ) {
            // 必要に応じてビーコン情報をここに書いてもOKだが、
            // BleScanManager側の定義を参照しても良い
            // （デモとして同じ定義をここで再掲）
            val beacons = listOf(
                BeaconInfo("MyCustomBeacon1", 2.0, 13.5, -59, 2.0),
                BeaconInfo("MyCustomBeacon2", 0.0, 10.0, -59, 2.0),
                BeaconInfo("MyCustomBeacon3", 2.0, 11.5, -59, 2.0),
                BeaconInfo("MyCustomBeacon4", 0.0, 13.5, -59, 2.0)
            )
            PositionMap(
                ma60Position = bleScanManager.estimatedPosMA60,
                kalmanPosition = bleScanManager.estimatedPosKalman,
                beacons = beacons
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // スキャン結果一覧
        Text(text = "Scan Results:")
        LazyColumn {
            items(bleScanManager.scanResults) { device ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Name: ${device.name}")
                    Text("Address: ${device.address}")
                    Text("RSSI: ${device.rssi}")
                }
            }
        }
    }
}

@Composable
fun PositionMap(
    ma60Position: Pair<Double, Double>?,
    kalmanPosition: Pair<Double, Double>?,
    beacons: List<BeaconInfo>
) {
    val minX = beacons.minOf { it.x }
    val maxX = beacons.maxOf { it.x }
    val minY = beacons.minOf { it.y }
    val maxY = beacons.maxOf { it.y }

    val margin = 20f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        val realW = (maxX - minX).coerceAtLeast(0.1)
        val realH = (maxY - minY).coerceAtLeast(0.1)

        val scaleX = (canvasW - margin * 2) / realW
        val scaleY = (canvasH - margin * 2) / realH

        // ビーコン描画（灰色）
        beacons.forEach { beacon ->
            val bx = margin + (beacon.x - minX) * scaleX
            val by = margin + (beacon.y - minY) * scaleY
            drawCircle(
                color = Color.DarkGray,
                radius = 10f,
                center = androidx.compose.ui.geometry.Offset(bx.toFloat(), by.toFloat())
            )
        }

        // MA(60) 緑色
        ma60Position?.let {
            drawPositionCircle(
                it, minX, minY, scaleX.toFloat(), scaleY.toFloat(), margin, Color.Green
            )
        }

        // Kalman(10->Adaptive) 青色
        kalmanPosition?.let {
            drawPositionCircle(
                it, minX, minY, scaleX.toFloat(), scaleY.toFloat(), margin, Color.Blue
            )
        }
    }
}

fun DrawScope.drawPositionCircle(
    pos: Pair<Double, Double>,
    minX: Double,
    minY: Double,
    scaleX: Float,
    scaleY: Float,
    margin: Float,
    color: Color
) {
    val x = margin + (pos.first - minX) * scaleX
    val y = margin + (pos.second - minY) * scaleY
    drawCircle(color, radius = 10f, center = androidx.compose.ui.geometry.Offset(x.toFloat(),
        y.toFloat()
    ))
}

fun formatPos(pos: Pair<Double, Double>?): String =
    if (pos == null) "null" else "x=%.2f, y=%.2f".format(pos.first, pos.second)
