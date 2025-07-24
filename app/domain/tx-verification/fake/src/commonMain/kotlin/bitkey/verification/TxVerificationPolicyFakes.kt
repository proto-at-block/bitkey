package bitkey.verification

import bitkey.privilegedaction.OutOfBandPrivilegedActionInstanceFake

val ActiveTxVerificationPolicyFake = TxVerificationPolicy.Active(
  threshold = VerificationThreshold.Always
)

val PendingTxVerificationPolicyFake = TxVerificationPolicy.Pending(
  authorization = OutOfBandPrivilegedActionInstanceFake
)
