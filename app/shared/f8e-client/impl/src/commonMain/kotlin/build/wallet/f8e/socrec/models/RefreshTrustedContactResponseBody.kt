package build.wallet.f8e.socrec.models

import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.Serializable

@Serializable
internal data class RefreshTrustedContactResponseBody(
  val invitation: CreateTrustedContactInvitation,
) : RedactedResponseBody
