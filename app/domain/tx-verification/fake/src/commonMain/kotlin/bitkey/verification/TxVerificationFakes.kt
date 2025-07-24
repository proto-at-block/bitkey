package bitkey.verification

import kotlinx.datetime.Instant

val FakePendingVerification = PendingTransactionVerification(
  id = TxVerificationId("fake-verification-id"),
  expiration = Instant.fromEpochSeconds(1234)
)

val FakeTxVerificationApproval = TxVerificationApproval(
  version = 0,
  hwAuthPublicKey = "fake-hw-auth-public-key",
  commitment = "fake-commitment",
  signature = "fake-signature",
  reverseHashChain = listOf("fake-reverse-hash-1", "fake-reverse-hash-2")
)
