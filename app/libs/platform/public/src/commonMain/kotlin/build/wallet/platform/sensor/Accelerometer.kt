package build.wallet.platform.sensor

import kotlinx.coroutines.flow.Flow

data class SensorData(val x: Int, val y: Int)

/**
 * Provides normalized system accelerometer data via [sensorEvents] flow.
 */
interface Accelerometer {
  val sensorEvents: Flow<SensorData>
}
