package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.f8e.privilegedactions.PrivilegedActionsF8eClient
import build.wallet.coroutines.flow.ConfirmationFlow
import build.wallet.coroutines.flow.ConfirmationState
import build.wallet.coroutines.flow.pollForConfirmation
import build.wallet.account.AccountService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class HardwareVerificationPrivilegedActionServiceImpl(
  f8eHttpClient: F8eHttpClient,
  override val accountService: AccountService,
  override val clock: Clock,
) : HardwareVerificationPrivilegedActionService, PrivilegedActionService<Unit, Unit> {
  override val privilegedActionF8eClient: PrivilegedActionsF8eClient<Unit, Unit> =
    object : PrivilegedActionsF8eClient<Unit, Unit> {
      override val f8eHttpClient: F8eHttpClient = f8eHttpClient
    }

  override suspend fun getPendingHardwareVerificationAction():
    Result<PrivilegedActionInstance?, PrivilegedActionError> {
    return getPrivilegedActionsByType(PrivilegedActionType.VERIFY_HARDWARE_SERIAL)
      .flatMap { actions ->
        when (actions.size) {
          0 -> Ok(null)
          1 -> Ok(actions.first().instance)
          else -> Err(PrivilegedActionError.MultiplePendingActionsFound)
        }
      }
  }

  override fun pollPendingHardwareVerificationAction(): ConfirmationFlow<Unit> {
    return pollForConfirmation {
      getPendingHardwareVerificationAction()
        .onFailure {
          logWarn { "Unexpected error polling pending hardware verification action: $it. Ignoring" }
        }
        .map { pendingAction ->
          if (pendingAction == null) {
            ConfirmationState.Confirmed(Unit)
          } else {
            ConfirmationState.Pending
          }
        }
        .getOrElse { ConfirmationState.Pending }
    }
  }
}
