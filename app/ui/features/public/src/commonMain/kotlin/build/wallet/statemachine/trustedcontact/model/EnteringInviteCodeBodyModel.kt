package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant.Feature
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.TextTransformation.INVITE_CODE
import build.wallet.ui.model.toolbar.ToolbarModel

data class EnteringInviteCodeBodyModel(
  val value: String = "",
  val onValueChange: (String) -> Unit,
  override val primaryButton: ButtonModel,
  val retreat: Retreat,
  val variant: TrustedContactFeatureVariant,
) : FormBodyModel(
    id = if (variant is TrustedContactFeatureVariant.Direct && variant.target == Feature.Inheritance) {
      SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_ENTER_INVITE_CODE
    } else {
      SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE
    },
    onSwipeToDismiss = retreat.onRetreat,
    onBack = retreat.onRetreat,
    toolbar = ToolbarModel(leadingAccessory = retreat.leadingToolbarAccessory),
    header = FormHeaderModel(
      headline = if (variant.isInheritanceEnabled) "Enter invite code" else "Enter invite code to accept",
      subline = when (variant) {
        is TrustedContactFeatureVariant.Generic -> when {
          variant.isInheritanceEnabled -> "Enter your invite code to accept a beneficiary or Trusted Contact invitation."
          else -> "Use the code that your Trusted Contact sent you to help safeguard their wallet."
        }
        is TrustedContactFeatureVariant.Direct -> when (variant.target) {
          Feature.Inheritance -> "Enter your invite code to accept a beneficiary invitation."
          Feature.Recovery -> "Use the code that your Trusted Contact sent you to help safeguard their wallet."
        }
      }
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.TextInput(
        fieldModel = TextFieldModel(
          value = value,
          placeholderText = "Invite code",
          transformation = INVITE_CODE,
          onValueChange = { newValue, _ ->
            onValueChange(newValue.replace("-", "").chunked(4).joinToString("-"))
          },
          keyboardType = TextFieldModel.KeyboardType.Default,
          onDone = if (primaryButton.isEnabled) {
            primaryButton.onClick::invoke
          } else {
            null
          }
        )
      )
    ),
    primaryButton = primaryButton
  )
