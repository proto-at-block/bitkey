package build.wallet.platform.device

internal fun deviceLockStateFromIsDeviceSecure(isDeviceSecure: Boolean?): DeviceLockState =
  when (isDeviceSecure) {
    true -> DeviceLockState.Enabled
    false -> DeviceLockState.Disabled
    null -> DeviceLockState.Unknown
  }

internal fun deviceLockStateFromPolicyEvaluation(
  canEvaluate: Boolean,
  errorDomain: String?,
  errorCode: Long?,
  passcodeNotSetErrorDomain: String,
  passcodeNotSetErrorCode: Long,
): DeviceLockState =
  when {
    canEvaluate -> DeviceLockState.Enabled
    errorDomain == passcodeNotSetErrorDomain && errorCode == passcodeNotSetErrorCode ->
      DeviceLockState.Disabled
    else -> DeviceLockState.Unknown
  }
