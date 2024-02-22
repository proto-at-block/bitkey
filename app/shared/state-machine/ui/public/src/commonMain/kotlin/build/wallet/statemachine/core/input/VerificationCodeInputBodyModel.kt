package build.wallet.statemachine.core.input

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Number
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

fun VerificationCodeInputBodyModel(
  title: String,
  subtitle: String,
  value: String = "",
  resendCodeContent: ResendCodeContent,
  skipForNowContent: SkipForNowContent,
  explainerText: String?,
  errorOverlay: SheetModel? = null,
  onValueChange: (String) -> Unit,
  onBack: () -> Unit,
  id: EventTrackerScreenId?,
) = ScreenModel(
  body =
    FormBodyModel(
      id = id,
      onBack = onBack,
      toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
      header = FormHeaderModel(headline = title, subline = subtitle),
      mainContentList =
        buildList {
          add(
            VerificationCodeInput(
              fieldModel =
                TextFieldModel(
                  value = value,
                  placeholderText = "Verification code",
                  onValueChange = { newValue, _ -> onValueChange(newValue) },
                  keyboardType = Number,
                  masksText = false
                ),
              resendCodeContent = resendCodeContent,
              skipForNowContent = skipForNowContent
            )
          )
          explainerText?.let {
            add(
              Explainer(
                items =
                  immutableListOf(
                    Statement(
                      leadingIcon = Icon.SmallIconWarning,
                      title = null,
                      body = explainerText
                    )
                  )
              )
            )
          }
        }.toImmutableList(),
      primaryButton = null
    ),
  presentationStyle = ScreenPresentationStyle.Modal,
  bottomSheetModel = errorOverlay
)

fun ResendCodeErrorSheet(
  isConnectivityError: Boolean,
  onDismiss: () -> Unit,
) = SheetModel(
  onClosed = onDismiss,
  body =
    ErrorFormBodyModel(
      title = "We were unable to resend the verification code",
      subline =
        when {
          isConnectivityError -> "Make sure you are connected to the internet and try again."
          else -> "We are looking into this. Please try again later."
        },
      primaryButton = ButtonDataModel("Done", onClick = onDismiss),
      renderContext = Sheet,
      eventTrackerScreenId = NotificationsEventTrackerScreenId.RESEND_CODE_ERROR_SHEET
    )
)
