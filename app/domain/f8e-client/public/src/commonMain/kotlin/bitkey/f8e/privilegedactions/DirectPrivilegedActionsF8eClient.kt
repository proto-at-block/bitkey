package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface DirectPrivilegedActionsF8eClient<Req, Res> : PrivilegedActionsF8eClient<Req, Res> {
  /**
   * Create a privileged action instance
   */
  suspend fun createPrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: Req,
  ): Result<PrivilegedActionInstance, Throwable>

  /**
   * Continue a privileged action instance after it's authorized
   */
  suspend fun continuePrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: ContinuePrivilegedActionRequest,
  ): Result<Res, Throwable>
}
