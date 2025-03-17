package build.wallet.f8e.relationships.models

import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.Serializable

@Serializable
internal data class RefreshTrustedContactResponseBody(
  val invitation: RelationshipInvitation,
) : RedactedResponseBody
