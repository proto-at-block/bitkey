package bitkey.ui.screens.securityhub.education

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.securitycenter.SecurityActionType
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.button.ButtonModel

data class SecurityHubEducationBodyModel(
  val actionType: SecurityActionType,
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = null,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      customContent = FormHeaderModel.PosterImage(
        icon = actionType.icon()
      ),
      iconModel = null,
      headline = actionType.headline(),
      subline = actionType.subline()
    ),
    primaryButton = actionType.primaryButton(onContinue),
    secondaryButton = actionType.secondaryButton(onBack)
  ) {
  @Composable
  override fun render(modifier: Modifier) {
    FormScreen(this)
  }
}

private fun SecurityActionType.icon(): Icon {
  return when (this) {
    SecurityActionType.EEK_BACKUP -> Icon.SecurityHubEducationEmergencyExit
    SecurityActionType.FINGERPRINTS -> Icon.SecurityHubEducationMultipleFingerprints
    SecurityActionType.SOCIAL_RECOVERY -> Icon.SecurityHubEducationTrustedContact
    SecurityActionType.CRITICAL_ALERTS -> Icon.SecurityHubEducationCriticalAlerts
    else -> error("Unsupported action type: $this")
  }
}

private fun SecurityActionType.headline(): String {
  return when (this) {
    SecurityActionType.EEK_BACKUP -> "Emergency Exit Kit"
    SecurityActionType.FINGERPRINTS -> "Multiple fingerprints"
    SecurityActionType.SOCIAL_RECOVERY -> "Recovery Contacts"
    SecurityActionType.CRITICAL_ALERTS -> "Critical alerts"
    else -> error("Unsupported action type: $this")
  }
}

private fun SecurityActionType.subline(): String {
  return when (this) {
    SecurityActionType.SOCIAL_RECOVERY -> """
      If you lose both your Bitkey app and device at the same time, a Recovery Contact can help you get back into your wallet.
      
      They never have access to your wallet, keys, or bitcoin—just a one-time code you generate during recovery to confirm you’re the owner.
      
      Without a Recovery Contact, you won’t be able to recover your wallet if you lose both your app and device at the same time.
    """.trimIndent()
    SecurityActionType.FINGERPRINTS -> """
      Scrapes or cuts can temporarily stop a fingerprint from scanning, making it harder to unlock your Bitkey.
      
      Adding multiple fingerprints is quick and can help avoid the hassle of a lockout.
    """.trimIndent()
    SecurityActionType.EEK_BACKUP -> """
      The Emergency Exit Kit is a pdf that links to a version of the Bitkey app stored on GitHub—not our servers.
      
      If Bitkey’s app or servers are ever blocked or go offline, you can still move your money using your Bitkey device and the key stored in your Emergency Exit Kit.
    """.trimIndent()
    SecurityActionType.CRITICAL_ALERTS -> """
      Critical alerts are sent when someone starts a recovery or inheritance process for your wallet, or changes your security settings.
      
      The more alert channels you enable, the easier it is to stay informed and in control of your money.
    """.trimIndent()
    else -> error("Unsupported action type: $this")
  }
}

private fun SecurityActionType.primaryButton(onClick: () -> Unit): ButtonModel {
  return when (this) {
    SecurityActionType.SOCIAL_RECOVERY, SecurityActionType.FINGERPRINTS, SecurityActionType.CRITICAL_ALERTS -> ButtonModel(
      text = "Continue",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onClick
    )
    SecurityActionType.EEK_BACKUP -> ButtonModel(
      text = "Got it",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onClick
    )
    else -> error("Unsupported action type: $this")
  }
}

private fun SecurityActionType.secondaryButton(onClick: () -> Unit): ButtonModel? {
  return when (this) {
    SecurityActionType.SOCIAL_RECOVERY, SecurityActionType.FINGERPRINTS, SecurityActionType.CRITICAL_ALERTS -> ButtonModel(
      text = "Set up later",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = onClick
    )
    SecurityActionType.EEK_BACKUP -> null
    else -> error("Unsupported action type: $this")
  }
}
