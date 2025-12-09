package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Privileged actions client where the resulting action may not require further authorization.
 */
interface OptionalPrivilegedActionsF8eClient<Req, Res> : PrivilegedActionsF8eClient<Req, Res> {
  /**
   * Send the action's request, resulting in either an immediate response or a privileged action instance.
   */
  suspend fun requestAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: Req,
  ): Result<OptionalPrivilegedAction<Res>, Throwable>
}
