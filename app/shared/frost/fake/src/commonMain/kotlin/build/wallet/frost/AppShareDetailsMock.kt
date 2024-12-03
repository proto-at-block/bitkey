package build.wallet.frost

val AppShareDetailsMock = ShareDetailsMock(
  secretShare = emptyList(),
  keyCommitments = KeyCommitmentsMock(
    vssCommitments = emptyList(),
    aggregatePublicKey = FrostPublicKeyMock("")
  )
)
