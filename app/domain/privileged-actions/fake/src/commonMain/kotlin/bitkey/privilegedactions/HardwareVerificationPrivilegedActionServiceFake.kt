package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.coroutines.flow.ConfirmationFlow
import build.wallet.coroutines.flow.ConfirmationState
import build.wallet.coroutines.flow.pollForConfirmation
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map

class HardwareVerificationPrivilegedActionServiceFake : HardwareVerificationPrivilegedActionService {
  var pendingHardwareVerificationAction: PrivilegedActionInstance? = null
  var getPendingHardwareVerificationActionResult:
    Result<PrivilegedActionInstance?, PrivilegedActionError>? = null

  override suspend fun getPendingHardwareVerificationAction():
    Result<PrivilegedActionInstance?, PrivilegedActionError> {
    return getPendingHardwareVerificationActionResult ?: Ok(pendingHardwareVerificationAction)
  }

  override fun pollPendingHardwareVerificationAction(): ConfirmationFlow<Unit> {
    return pollForConfirmation {
      getPendingHardwareVerificationAction()
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
