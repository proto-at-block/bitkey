package build.wallet.statemachine.account.create.full.hardware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_FINGERPRINT_ENROLLMENT_HELP
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.CustomContent
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TapBitkey
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContentModel
import build.wallet.ui.components.explainer.Statement as ExplainerStatement
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.persistentListOf

/**
 * W3 onboarding setup help screen.
 * Shown when user taps the question mark icon on the "Set up your Bitkey" screen.
 */
class FingerprintEnrollmentHelpBodyModel(
  onBack: () -> Unit,
  eventTrackerContext: EventTrackerContext,
  devicePlatform: DevicePlatform = DevicePlatform.Jvm,
) : FormBodyModel(
    id = HW_FINGERPRINT_ENROLLMENT_HELP,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack)
    ),
    formScreenTitle = FormScreenTitleModel(title = "How it works"),
    formScreenLayout = FormScreenLayoutModel.LargeTitle(),
    header = null,
    mainContentList = persistentListOf(
      CustomContent(
        item = SetupBitkeyHelpContentModel(devicePlatform)
      )
    ),
    primaryButton = null,
    eventTrackerContext = eventTrackerContext
  )

private data class SetupBitkeyHelpContentModel(
  private val devicePlatform: DevicePlatform,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      SectionTitle("How to complete an NFC tap")

      HardwareConfirmationHelpContentModel(
        content = TapBitkey,
        devicePlatform = devicePlatform
      ).render(Modifier.fillMaxWidth())

      AsteriskDivider()

      SectionTitle("How to set up your fingerprint")

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        fingerprintStatements.forEachIndexed { index, statement ->
          ExplainerStatement(
            title = statement.title,
            body = statement.body,
            icon = null,
            leadingText = "[${index + 1}]",
            leadingTextType = LabelType.Body2MonoCaps,
            leadingTextTreatment = LabelTreatment.Primary,
            titleType = LabelType.Body2MonoCaps,
            titleTreatment = LabelTreatment.Primary,
            bodyType = LabelType.Body3Regular,
            bodyTreatment = LabelTreatment.Secondary
          )
        }
      }
    }
  }

  private data class FingerprintStatement(
    val title: String,
    val body: String,
  )

  private companion object {
    val fingerprintStatements = listOf(
      FingerprintStatement(
        title = "TAP, LIFT, AND REPEAT",
        body = "Touch the fingerprint sensor on your Bitkey—then lift and repeat."
      ),
      FingerprintStatement(
        title = "GET ALL SIDES OF YOUR FINGER",
        body = "Make sure you move your finger around for a complete capture."
      ),
      FingerprintStatement(
        title = "FINISH ON YOUR PHONE",
        body = "When finished, return to your phone to save your fingerprint."
      )
    )
  }
}

@Composable
private fun SectionTitle(text: String) {
  Label(
    text = text,
    type = LabelType.Body2MonoCaps,
    treatment = LabelTreatment.Primary
  )
}

@Composable
private fun AsteriskDivider() {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    repeat(6) {
      Label(
        text = "*",
        type = LabelType.Body2MonoCaps,
        treatment = LabelTreatment.Primary
      )
    }
  }
}
