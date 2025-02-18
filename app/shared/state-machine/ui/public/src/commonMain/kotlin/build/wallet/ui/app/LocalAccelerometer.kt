package build.wallet.ui.app

import androidx.compose.runtime.compositionLocalOf
import build.wallet.platform.sensor.Accelerometer

val LocalAccelerometer = compositionLocalOf<Accelerometer?> { null }
