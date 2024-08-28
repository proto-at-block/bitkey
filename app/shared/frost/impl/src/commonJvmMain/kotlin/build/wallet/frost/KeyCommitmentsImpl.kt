package build.wallet.frost

import build.wallet.rust.core.KeyCommitments as FfiKeyCommitments

data class KeyCommitmentsImpl(
  val ffiKeyCommitments: FfiKeyCommitments,
) : KeyCommitments
