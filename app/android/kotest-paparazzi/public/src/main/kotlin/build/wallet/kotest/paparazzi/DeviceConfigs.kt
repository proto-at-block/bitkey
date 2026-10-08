package build.wallet.kotest.paparazzi

import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen

/*
 * Device configs for devices Paparazzi doesn't provide, declared in the same style as
 * Paparazzi's own [DeviceConfig.PIXEL_6].
 *
 * iPhone Duo specs come from the iOS simulator device profile
 * (`iPhone Duo.simdevicetype/Contents/Resources/capabilities.plist`, model iPhone19,4):
 * 460ppi panels rendered at 3x scale. Density is [Density.XXHIGH] (3x) so Android dp map 1:1 to
 * iOS points, giving shared Compose layouts the same constraints they get on the device.
 */

/** iPhone Duo folded (outer display), portrait: 1398×2034px / 466×678dp. */
val DeviceConfig.Companion.IPHONE_DUO_FOLDED: DeviceConfig
  get() = iPhoneDuo(
    screenWidth = 1398,
    screenHeight = 2034,
    orientation = ScreenOrientation.PORTRAIT,
    size = ScreenSize.NORMAL
  )

/** iPhone Duo unfolded (inner display), landscape: 2853×2007px / 951×669dp. */
val DeviceConfig.Companion.IPHONE_DUO_UNFOLDED: DeviceConfig
  get() = iPhoneDuo(
    screenWidth = 2853,
    screenHeight = 2007,
    orientation = ScreenOrientation.LANDSCAPE,
    size = ScreenSize.LARGE
  )

private fun iPhoneDuo(
  screenWidth: Int,
  screenHeight: Int,
  orientation: ScreenOrientation,
  size: ScreenSize,
) = DeviceConfig(
  screenHeight = screenHeight,
  screenWidth = screenWidth,
  xdpi = 460,
  ydpi = 460,
  orientation = orientation,
  density = Density.XXHIGH,
  ratio = ScreenRatio.NOTLONG,
  size = size,
  keyboard = Keyboard.NOKEY,
  touchScreen = TouchScreen.FINGER,
  keyboardState = KeyboardState.SOFT,
  softButtons = false,
  navigation = Navigation.NONAV,
  released = "iPhone Duo (iPhone19,4) iOS simulator profile"
)
