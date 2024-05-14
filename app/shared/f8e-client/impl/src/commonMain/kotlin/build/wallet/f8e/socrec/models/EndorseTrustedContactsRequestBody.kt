package build.wallet.f8e.socrec.models

import build.wallet.ktor.result.RedactedRequestBody
import kotlinx.serialization.Serializable

@Serializable
internal data class EndorseTrustedContactsRequestBody(
  val endorsements: List<EndorsementBody>,
) : RedactedRequestBody
