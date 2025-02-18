package build.wallet.platform.sensor

import kotlinx.coroutines.flow.Flow

expect class AccelerometerImpl : Accelerometer {
  override val sensorEvents: Flow<SensorData>
}
