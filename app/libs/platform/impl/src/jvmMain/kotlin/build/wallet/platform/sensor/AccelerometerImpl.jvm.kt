package build.wallet.platform.sensor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class AccelerometerImpl : Accelerometer {
  actual override val sensorEvents: Flow<SensorData> = emptyFlow()
}
