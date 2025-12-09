package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.OptionalPrivilegedActionsF8eClient

/**
 * Privileged Actions service that can create a privileged actions request
 * that may or may not require further authorization.
 */
interface OptionalPrivilegedActionService<Req, Res> : PrivilegedActionService<Req, Res> {
  override val privilegedActionF8eClient: OptionalPrivilegedActionsF8eClient<Req, Res>
}
