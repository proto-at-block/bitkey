package build.wallet.statemachine.status

import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.EmergencyAccessMode
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InactiveApp
import build.wallet.availability.InternetUnreachable
import build.wallet.ui.model.alert.AlertModel

fun AppFunctionalityStatusAlertModel(
  status: AppFunctionalityStatus.LimitedFunctionality,
  onDismiss: () -> Unit,
) = AlertModel(
  title =
    when (status.cause) {
      is F8eUnreachable -> "Unable to reach Bitkey services"
      is InternetUnreachable -> "Offline"
      InactiveApp -> "Limited Functionality"
      EmergencyAccessMode -> "Limited Functionality"
    },
  subline =
    when (status.cause) {
      is F8eUnreachable -> "Some features may not be available"
      is InternetUnreachable -> "Some functionality may not be available until you’re connected to the internet."
      InactiveApp -> "Your wallet is active on another phone"
      EmergencyAccessMode -> "Some functionality is disabled in the Emergency Access Kit."
    },
  onDismiss = onDismiss,
  primaryButtonText = "OK",
  onPrimaryButtonClick = onDismiss
)
