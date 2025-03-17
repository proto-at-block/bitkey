package build.wallet.frost

class ShareDetailsMock(
  override val secretShare: List<UByte>,
  override val keyCommitments: KeyCommitments,
) : ShareDetails
