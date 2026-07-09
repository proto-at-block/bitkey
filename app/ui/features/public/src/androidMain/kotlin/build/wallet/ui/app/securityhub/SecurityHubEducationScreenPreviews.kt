@file:Suppress("detekt:TooManyFunctions")

package build.wallet.ui.app.securityhub

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import bitkey.securitycenter.SecurityActionType
import bitkey.ui.screens.securityhub.education.SecurityHubEducationBodyModel
import build.wallet.ui.model.render
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Emergency Exit Kit")
@Composable
fun EmergencyExitKitSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.EEK_BACKUP
  )
}

@Preview(name = "Multiple Fingerprints")
@Composable
fun MultipleFingerprintsSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.FINGERPRINTS
  )
}

@Preview(name = "Recovery Contacts")
@Composable
fun RecoveryContactsSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.SOCIAL_RECOVERY
  )
}

@Preview(name = "Critical Alerts")
@Composable
fun CriticalAlertsSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.CRITICAL_ALERTS
  )
}

@Preview(name = "Transaction Verification")
@Composable
fun TransactionVerificationSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.TRANSACTION_VERIFICATION
  )
}

@Composable
private fun SecurityHubEducationPreview(
  actionType: SecurityActionType,
) {
  PreviewWalletTheme {
    SecurityHubEducationBodyModel(
      actionType = actionType,
      onBack = {},
      onContinue = {}
    ).render()
  }
}
