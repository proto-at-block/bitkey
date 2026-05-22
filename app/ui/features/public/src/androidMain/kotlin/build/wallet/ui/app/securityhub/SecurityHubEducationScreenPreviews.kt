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

@Preview(name = "Emergency Exit Kit (Design System V2)")
@Composable
fun EmergencyExitKitSecurityHubEducationDesignSystemV2Preview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.EEK_BACKUP,
  )
}

@Preview(name = "Multiple Fingerprints")
@Composable
fun MultipleFingerprintsSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.FINGERPRINTS
  )
}

@Preview(name = "Multiple Fingerprints (Design System V2)")
@Composable
fun MultipleFingerprintsSecurityHubEducationDesignSystemV2Preview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.FINGERPRINTS,
  )
}

@Preview(name = "Recovery Contacts")
@Composable
fun RecoveryContactsSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.SOCIAL_RECOVERY
  )
}

@Preview(name = "Recovery Contacts (Design System V2)")
@Composable
fun RecoveryContactsSecurityHubEducationDesignSystemV2Preview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.SOCIAL_RECOVERY,
  )
}

@Preview(name = "Critical Alerts")
@Composable
fun CriticalAlertsSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.CRITICAL_ALERTS
  )
}

@Preview(name = "Critical Alerts (Design System V2)")
@Composable
fun CriticalAlertsSecurityHubEducationDesignSystemV2Preview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.CRITICAL_ALERTS,
  )
}

@Preview(name = "Transaction Verification")
@Composable
fun TransactionVerificationSecurityHubEducationPreview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.TRANSACTION_VERIFICATION
  )
}

@Preview(name = "Transaction Verification (Design System V2)")
@Composable
fun TransactionVerificationSecurityHubEducationDesignSystemV2Preview() {
  SecurityHubEducationPreview(
    actionType = SecurityActionType.TRANSACTION_VERIFICATION,
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
