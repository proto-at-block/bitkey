@file:OptIn(ExperimentalForeignApi::class)

package build.wallet.platform.sensor

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.math.roundToInt

@BitkeyInject(AppScope::class)
actual class AccelerometerImpl(
  appScope: CoroutineScope,
) : Accelerometer {
  actual override val sensorEvents: Flow<SensorData> =
    callbackFlow {
      val motionManager = CMMotionManager()
      if (motionManager.accelerometerAvailable) {
        motionManager.startAccelerometerUpdatesToQueue(
          NSOperationQueue.mainQueue
        ) { accelerometerData, _ ->
          accelerometerData
            ?.acceleration
            ?.useContents {
              // Shift decimal and scale up value to match Android magnitudes.
              SensorData(
                x = (x * 10 * 2).roundToInt(),
                y = (y * 10 * 2).roundToInt()
              )
            }
            ?.also { trySend(it) }
        }
      }
      awaitClose {
        if (motionManager.accelerometerActive) {
          motionManager.stopAccelerometerUpdates()
        }
      }
    }.distinctUntilChanged()
      .shareIn(appScope, SharingStarted.WhileSubscribed())
}
