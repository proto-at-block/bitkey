package build.wallet.statemachine.fwup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.progress.IndeterminateCircularProgressIndicator
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.WalletTheme

data class FwupNfcCooldownModel(
  override val onBack: () -> Unit,
  val remainingSeconds: Int,
  val onContinue: (() -> Unit)? = null,
  val isStartingSession: Boolean = false,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo =
    EventTrackerScreenInfo(FwupEventTrackerScreenId.FWUP_NFC_SESSION_COOLDOWN)

  @Composable
  override fun render(modifier: Modifier) {
    val headerModel =
      FormHeaderModel(
        headline =
          when {
            isStartingSession -> "Starting session..."
            remainingSeconds > 1 -> "Continue the update in $remainingSeconds seconds"
            remainingSeconds == 1 -> "Continue the update in 1 second"
            else -> "Continue the update"
          },
        subline =
          when {
            isStartingSession -> "Hold your Bitkey to your phone to continue the update."
            remainingSeconds > 0 ->
              "Giving your phone a quick rest before continuing. Move your Bitkey away from your phone. Your progress is saved."
            else -> "Your phone is ready to continue. Your progress is saved."
          }
      )

    val continueButtonModel =
      onContinue?.let { continueAction ->
        ButtonModel(
          text = "Continue",
          requiresBitkeyInteraction = true,
          treatment = ButtonModel.Treatment.Primary,
          size = ButtonModel.Size.Footer,
          onClick = continueAction
        )
      }
    val cancelButtonModel =
      ButtonModel(
        text = "Cancel",
        requiresBitkeyInteraction = false,
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = onBack
      )

    // Use FormScreen directly instead of FormBodyModel because this state needs the shared
    // form chrome plus a custom circular loader. FormBodyModel only gives us the standard
    // form loader content, which does not match this cooldown design.
    FormScreen(
      modifier = modifier,
      onBack = onBack,
      layout = FormScreenLayoutModel.LargeTitle(
        scrollable = false,
        mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
      ),
      headerContent = {
        Header(model = headerModel)
      },
      mainContent = {
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          if (isStartingSession || remainingSeconds > 0) {
            IndeterminateCircularProgressIndicator(
              indicatorColor = WalletTheme.colors.bitkeyPrimary,
              trackColor = WalletTheme.colors.foreground10,
              strokeWidth = 6.dp,
              size = 84.dp
            )
          }
        }
      },
      footerContent = {
        continueButtonModel?.let {
          Button(model = it)
          Spacer(modifier = Modifier.height(16.dp))
        }
        Button(model = cancelButtonModel)
        Spacer(modifier = Modifier.height(24.dp))
      }
    )
  }
}
