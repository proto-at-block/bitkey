package build.wallet.bitkey.relationships

import dev.zacsweers.redacted.annotations.Redacted

/**
 * An invitation, as seen by the Protected Customer, to become a Trusted Contact. This wraps
 * the [Invitation] returned by the server with PAKE enrollment authentication data.
 */
data class OutgoingInvitation(
  val invitation: Invitation,
  @Redacted
  val inviteCode: String,
)
