package build.wallet.frost

class KeyCommitmentsMock(
  override val vssCommitments: List<PublicKey>,
  override val aggregatePublicKey: PublicKey,
) : KeyCommitments
