package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.PrivilegedActionsF8eClient
import build.wallet.account.AccountService
import kotlinx.datetime.Clock

/**
 * Base interface for privileged action services.
 *
 * Use [DirectPrivilegedActionService] if implementing a service with DirectPrivilegedActionsF8eClient
 */
interface PrivilegedActionService<Req, Res> {
  val privilegedActionF8eClient: PrivilegedActionsF8eClient<Req, Res>
  val accountService: AccountService
  val clock: Clock
}
