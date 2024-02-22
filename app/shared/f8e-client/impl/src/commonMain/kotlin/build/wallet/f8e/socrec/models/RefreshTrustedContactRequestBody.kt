package build.wallet.f8e.socrec.models

import kotlinx.serialization.Serializable

@Serializable
internal data class RefreshTrustedContactRequestBody internal constructor(
  val action: String,
) {
  constructor() : this("Reissue")
}
