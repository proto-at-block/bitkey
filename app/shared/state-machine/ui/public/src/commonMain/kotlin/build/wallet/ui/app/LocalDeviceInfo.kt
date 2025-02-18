package build.wallet.ui.app

import androidx.compose.runtime.compositionLocalOf
import build.wallet.platform.device.DeviceInfo

/**
 * Provides the current [DeviceInfo] for use in UI code that
 * must adapt to a specific platform.
 */
val LocalDeviceInfo = compositionLocalOf<DeviceInfo> { error("No device info provided") }
