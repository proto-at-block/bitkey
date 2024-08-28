package build.wallet.frost

import build.wallet.rust.core.ShareDetails as FfiShareDetails

data class ShareDetailsImpl(
  val shareDetails: FfiShareDetails,
) : ShareDetails
