package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
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

class NfcHelpBodyModel(
  onBack: () -> Unit,
) : FormBodyModel(
    id = NfcEventTrackerScreenId.NFC_HELP,
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
            title = "REMOVE YOUR PHONE CASE",
            body = StringModel("Thick cases or magnetic accessories may block the signal. Also remove any cards (e.g. IDs, credit cards, keycards) stored in or behind the case.")
          ),
          Statement(
            leadingIcon = Icon.SmallIconDigitTwo,
            title = "FIND THE RIGHT POSITION",
            body = StringModel("Slowly move your Bitkey device from the top to the bottom of your phone\u2019s back until the NFC connection is detected.")
          )
        )
      )
    ),
    primaryButton = null,
    designSystemV2Model = FormDesignSystemV2Model(
      mainContentList = persistentListOf(
        Explainer(
          items = persistentListOf(
            Statement(
              leadingText = "[1]",
              leadingTextType = LabelType.Body2MonoCaps,
              leadingTextLabelTreatment = LabelTreatment.Primary,
              title = "REMOVE YOUR PHONE CASE",
              body = StringModel("Thick cases or magnetic accessories may block the signal. Also remove any cards (e.g. IDs, credit cards, keycards) stored in or behind the case."),
              titleLabelType = LabelType.Body2MonoCaps,
              titleLabelTreatment = LabelTreatment.Primary,
              bodyType = LabelType.Body2Regular,
              bodyLabelTreatment = LabelTreatment.Secondary
            ),
            Statement(
              leadingText = "[2]",
              leadingTextType = LabelType.Body2MonoCaps,
              leadingTextLabelTreatment = LabelTreatment.Primary,
              title = "FIND THE RIGHT POSITION",
              body = StringModel("Slowly move your Bitkey device from the top to the bottom of your phone\u2019s back until the NFC connection is detected."),
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
