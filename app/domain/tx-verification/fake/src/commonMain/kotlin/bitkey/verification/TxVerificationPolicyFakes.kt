package bitkey.verification

import kotlinx.datetime.Instant

val TxVerificationPolicyAuthFake = TxVerificationPolicy.DelayNotifyAuthorization(
  id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId("fake-auth-id"),
  delayEndTime = Instant.fromEpochSeconds(123L),
  cancellationToken = "fake-cancellation-token",
  completionToken = "fake-completion-token"
)

val ActiveTxVerificationPolicyFake = TxVerificationPolicy.Active(
  id = TxVerificationPolicy.PolicyId(123L),
  threshold = VerificationThreshold.Always
)

val PendingTxVerificationPolicyFake = TxVerificationPolicy.Pending(
  id = TxVerificationPolicy.PolicyId(123L),
  threshold = VerificationThreshold.Always,
  authorization = TxVerificationPolicyAuthFake
)
