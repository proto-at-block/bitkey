package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_FINGERPRINT_ENROLLMENT_HELP
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.persistentListOf

/**
 * W3 onboarding fingerprint help screen.
 * Shown when user taps the question mark icon on the "Finished on your device?" screen.
 */
class FingerprintEnrollmentHelpBodyModel(
  onBack: () -> Unit,
  eventTrackerContext: EventTrackerContext,
) : FormBodyModel(
    id = HW_FINGERPRINT_ENROLLMENT_HELP,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack)
    ),
    header = FormHeaderModel(
      headline = "How it works"
    ),
    mainContentList = persistentListOf(
      Explainer(
        items = persistentListOf(
          Statement(
            leadingIcon = Icon.SmallIconDigitOne,
            title = "TAP, LIFT, AND REPEAT",
            body = StringModel("Touch the fingerprint sensor on your Bitkey—then lift and repeat.")
          ),
          Statement(
            leadingIcon = Icon.SmallIconDigitTwo,
            title = "GET ALL SIDES OF YOUR FINGER",
            body = StringModel("Make sure you move your finger around for a complete capture.")
          ),
          Statement(
            leadingIcon = Icon.SmallIconDigitThree,
            title = "FINISH ON YOUR PHONE",
            body = StringModel("When finished, return to your phone to save your fingerprint.")
          )
        )
      )
    ),
    primaryButton = null,
    eventTrackerContext = eventTrackerContext,
    designSystemV2Model = FormDesignSystemV2Model(
      mainContentList = persistentListOf(
        Explainer(
          items = persistentListOf(
            Statement(
              leadingText = "[1]",
              leadingTextType = LabelType.Body2MonoCaps,
              leadingTextLabelTreatment = LabelTreatment.Primary,
              title = "TAP, LIFT, AND REPEAT",
              body = StringModel("Touch the fingerprint sensor on your Bitkey—then lift and repeat."),
              titleLabelType = LabelType.Body2MonoCaps,
              titleLabelTreatment = LabelTreatment.Primary,
              bodyType = LabelType.Body2Regular,
              bodyLabelTreatment = LabelTreatment.Secondary
            ),
            Statement(
              leadingText = "[2]",
              leadingTextType = LabelType.Body2MonoCaps,
              leadingTextLabelTreatment = LabelTreatment.Primary,
              title = "GET ALL SIDES OF YOUR FINGER",
              body = StringModel("Make sure you move your finger around for a complete capture."),
              titleLabelType = LabelType.Body2MonoCaps,
              titleLabelTreatment = LabelTreatment.Primary,
              bodyType = LabelType.Body2Regular,
              bodyLabelTreatment = LabelTreatment.Secondary
            ),
            Statement(
              leadingText = "[3]",
              leadingTextType = LabelType.Body2MonoCaps,
              leadingTextLabelTreatment = LabelTreatment.Primary,
              title = "FINISH ON YOUR PHONE",
              body = StringModel("When finished, return to your phone to save your fingerprint."),
              titleLabelType = LabelType.Body2MonoCaps,
              titleLabelTreatment = LabelTreatment.Primary,
              bodyType = LabelType.Body2Regular,
              bodyLabelTreatment = LabelTreatment.Secondary
            )
          )
        )
      )
    )
  )
