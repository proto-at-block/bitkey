package build.wallet.f8e.socrec.models

import build.wallet.ktor.result.RedactedRequestBody
import kotlinx.serialization.Serializable

@Serializable
internal data class RefreshTrustedContactRequestBody internal constructor(
  val action: String,
) : RedactedRequestBody {
  constructor() : this("Reissue")
}
