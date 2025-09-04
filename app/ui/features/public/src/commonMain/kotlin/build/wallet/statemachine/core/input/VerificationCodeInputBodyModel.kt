package build.wallet.statemachine.core.input

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
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
import dev.zacsweers.redacted.annotations.Redacted

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
  body = VerificationCodeInputFormBodyModel(
    title = title,
    subtitle = subtitle,
    value = value,
    resendCodeContent = resendCodeContent,
    skipForNowContent = skipForNowContent,
    explainerText = explainerText,
    onValueChange = onValueChange,
    onBack = onBack,
    id = id
  ),
  presentationStyle = ScreenPresentationStyle.Modal,
  bottomSheetModel = errorOverlay
)

data class VerificationCodeInputFormBodyModel(
  val title: String,
  @Redacted
  val subtitle: String,
  val value: String,
  val resendCodeContent: ResendCodeContent,
  val skipForNowContent: SkipForNowContent,
  val explainerText: String?,
  val onValueChange: (String) -> Unit,
  override val onBack: () -> Unit,
  override val id: EventTrackerScreenId?,
) : FormBodyModel(
    id = id,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(headline = title, subline = subtitle),
    mainContentList =
      buildImmutableList {
        add(
          VerificationCodeInput(
            fieldModel =
              TextFieldModel(
                value = value,
                placeholderText = "Verification code",
                onValueChange = { newValue, _ -> onValueChange(newValue) },
                keyboardType = Number,
                transformation = null
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
      },
    primaryButton = null
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
