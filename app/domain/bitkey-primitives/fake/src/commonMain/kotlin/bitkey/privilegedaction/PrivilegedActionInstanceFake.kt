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
