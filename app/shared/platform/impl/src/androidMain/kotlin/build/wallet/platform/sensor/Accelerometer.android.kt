package build.wallet.platform.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
actual class AccelerometerImpl(
  private val sensorManager: SensorManager,
  private val appScope: CoroutineScope,
) : Accelerometer {
  actual override val sensorEvents: Flow<SensorData> =
    callbackFlow {
      val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
          if (event != null) {
            val values = event.values
            val x = values.getOrNull(0)?.toInt() ?: 0
            val y = values.getOrNull(1)?.toInt() ?: 0
            // Invert value to match iOS direction.
            trySend(SensorData(x = -x * 2, y = -y * 2))
          }
        }

        override fun onAccuracyChanged(
          sensor: Sensor?,
          accuracy: Int,
        ) = Unit
      }

      val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
      if (accelerometer != null) {
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
      }

      awaitClose {
        sensorManager.unregisterListener(listener)
      }
    }.distinctUntilChanged()
      .shareIn(appScope, SharingStarted.WhileSubscribed())
}
