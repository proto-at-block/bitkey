package build.wallet.frost

import build.wallet.rust.core.KeyCommitments as FfiKeyCommitments

data class KeyCommitmentsImpl(
  val ffiKeyCommitments: FfiKeyCommitments,
) : KeyCommitments {
  override val vssCommitments = ffiKeyCommitments.vssCommitments.map { PublicKeyImpl(it) }
  override val aggregatePublicKey = PublicKeyImpl(ffiKeyCommitments.aggregatePublicKey)
}
