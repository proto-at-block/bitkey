package build.wallet.analytics.events

import build.wallet.analytics.v1.DeviceLockState
import build.wallet.analytics.v1.DeviceLockState.DEVICE_LOCK_STATE_DISABLED
import build.wallet.analytics.v1.DeviceLockState.DEVICE_LOCK_STATE_ENABLED
import build.wallet.analytics.v1.DeviceLockState.DEVICE_LOCK_STATE_UNSPECIFIED
import build.wallet.analytics.v1.EmulatorState
import build.wallet.analytics.v1.EmulatorState.EMULATOR_STATE_DETECTED
import build.wallet.analytics.v1.EmulatorState.EMULATOR_STATE_NOT_DETECTED
import build.wallet.analytics.v1.StrongboxAvailability
import build.wallet.analytics.v1.StrongboxAvailability.STRONGBOX_AVAILABILITY_NOT_SUPPORTED
import build.wallet.analytics.v1.StrongboxAvailability.STRONGBOX_AVAILABILITY_SUPPORTED
import build.wallet.platform.device.DeviceLockState as PlatformDeviceLockState

internal fun analyticsDeviceLockState(deviceLockState: PlatformDeviceLockState): DeviceLockState =
  when (deviceLockState) {
    PlatformDeviceLockState.Enabled -> DEVICE_LOCK_STATE_ENABLED
    PlatformDeviceLockState.Disabled -> DEVICE_LOCK_STATE_DISABLED
    PlatformDeviceLockState.Unknown -> DEVICE_LOCK_STATE_UNSPECIFIED
  }

internal fun emulatorState(isEmulator: Boolean): EmulatorState =
  if (isEmulator) {
    EMULATOR_STATE_DETECTED
  } else {
    EMULATOR_STATE_NOT_DETECTED
  }

internal fun androidStrongboxAvailability(
  sdkInt: Int,
  hasStrongboxFeature: Boolean,
): StrongboxAvailability =
  if (sdkInt >= MIN_STRONGBOX_SDK_INT && hasStrongboxFeature) {
    STRONGBOX_AVAILABILITY_SUPPORTED
  } else {
    STRONGBOX_AVAILABILITY_NOT_SUPPORTED
  }

private const val MIN_STRONGBOX_SDK_INT = 28
