package bitkey.privilegedaction

import bitkey.privilegedactions.AuthorizationStrategy
import bitkey.privilegedactions.AuthorizationStrategyType
import bitkey.privilegedactions.PrivilegedActionInstance
import bitkey.privilegedactions.PrivilegedActionType

val OutOfBandPrivilegedActionInstanceFake = PrivilegedActionInstance(
  id = "fake-privileged-action-instance-id",
  privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
  authorizationStrategy = AuthorizationStrategy.OutOfBand(
    authorizationStrategyType = AuthorizationStrategyType.OUT_OF_BAND
  )
)

val VerifyHardwareSerialPrivilegedActionInstanceFake = PrivilegedActionInstance(
  id = "fake-verify-hardware-serial-privileged-action-instance-id",
  privilegedActionType = PrivilegedActionType.VERIFY_HARDWARE_SERIAL,
  authorizationStrategy = AuthorizationStrategy.OutOfBand(
    authorizationStrategyType = AuthorizationStrategyType.OUT_OF_BAND
  )
)
