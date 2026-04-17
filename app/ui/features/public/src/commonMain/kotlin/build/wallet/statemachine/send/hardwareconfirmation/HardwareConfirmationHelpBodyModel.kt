package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TransactionReview
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.persistentListOf

class HardwareConfirmationHelpBodyModel(
  onBack: () -> Unit,
  content: HardwareConfirmationHelpContent,
) : FormBodyModel(
    id = SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_HELP,
    eventTrackerShouldTrack = content != TransactionReview,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack)
    ),
    header = FormHeaderModel(
      headline = content.headline
    ),
    mainContentList = persistentListOf(
      Explainer(
        items =
          persistentListOf(
            Statement(
              leadingIcon = Icon.SmallIconDigitOne,
              title = content.statements[0].title,
              body = StringModel(content.statements[0].body)
            ),
            Statement(
              leadingIcon = Icon.SmallIconDigitTwo,
              title = content.statements[1].title,
              body = StringModel(content.statements[1].body)
            ),
            Statement(
              leadingIcon = Icon.SmallIconDigitThree,
              title = content.statements[2].title,
              body = StringModel(content.statements[2].body)
            )
          )
      )
    ),
    primaryButton = null,
    designSystemV2Model = FormDesignSystemV2Model(
      mainContentList = persistentListOf(
        Explainer(
          items =
            persistentListOf(
              Statement(
                leadingText = "[1]",
                leadingTextType = LabelType.Body2MonoCaps,
                leadingTextLabelTreatment = LabelTreatment.Primary,
                title = content.statements[0].title,
                body = StringModel(content.statements[0].body),
                titleLabelType = LabelType.Body2MonoCaps,
                titleLabelTreatment = LabelTreatment.Primary,
                bodyType = LabelType.Body2Regular,
                bodyLabelTreatment = LabelTreatment.Secondary
              ),
              Statement(
                leadingText = "[2]",
                leadingTextType = LabelType.Body2MonoCaps,
                leadingTextLabelTreatment = LabelTreatment.Primary,
                title = content.statements[1].title,
                body = StringModel(content.statements[1].body),
                titleLabelType = LabelType.Body2MonoCaps,
                titleLabelTreatment = LabelTreatment.Primary,
                bodyType = LabelType.Body2Regular,
                bodyLabelTreatment = LabelTreatment.Secondary
              ),
              Statement(
                leadingText = "[3]",
                leadingTextType = LabelType.Body2MonoCaps,
                leadingTextLabelTreatment = LabelTreatment.Primary,
                title = content.statements[2].title,
                body = StringModel(content.statements[2].body),
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
