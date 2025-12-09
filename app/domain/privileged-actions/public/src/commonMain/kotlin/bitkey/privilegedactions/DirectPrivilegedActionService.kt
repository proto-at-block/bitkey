package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.DirectPrivilegedActionsF8eClient

/**
 * Privileged Actions service that can create a privileged actions request
 * directly with no conditional steps.
 */
interface DirectPrivilegedActionService<Req, Res> : PrivilegedActionService<Req, Res> {
  override val privilegedActionF8eClient: DirectPrivilegedActionsF8eClient<Req, Res>
}
