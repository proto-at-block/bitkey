package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.context.AuthKeyRotationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

object RotateAuthKeyScreens {
  fun Confirmation(
    context: AuthKeyRotationEventTrackerScreenIdContext,
    onSelected: () -> Unit,
  ) = FormBodyModel(
    id = InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH,
    eventTrackerContext = context,
    onBack = null,
    toolbar = ToolbarModel(),
    header =
      FormHeaderModel(
        icon = Icon.LargeIconCheckFilled,
        headline = "All other devices removed",
        subline = "We’ve successfully removed all other devices that were associated with your Bitkey wallet."
      ),
    primaryButton =
      ButtonModel(
        "Done",
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onSelected)
      )
  )

  fun AcceptableFailure(
    context: AuthKeyRotationEventTrackerScreenIdContext,
    onRetry: () -> Unit,
    onAcknowledge: () -> Unit,
  ) = FormBodyModel(
    id = InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_ACCEPTABLE,
    eventTrackerContext = context,
    onBack = null,
    toolbar = ToolbarModel(),
    header =
      FormHeaderModel(
        icon = Icon.LargeIconCheckFilled,
        headline = "Something went wrong",
        subline = "We weren't able to remove all devices associated with your Bitkey wallet. " +
          "Please try again or you can cancel and remove devices at a later time within your Bitkey application settings."
      ),
    primaryButton =
      ButtonModel(
        "Try again",
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onRetry)
      ),
    secondaryButton =
      ButtonModel(
        "Cancel",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onAcknowledge)
      )
  )

  fun UnexpectedFailure(
    context: AuthKeyRotationEventTrackerScreenIdContext,
    onRetry: () -> Unit,
    onContactSupport: () -> Unit,
  ) = UnexpectedFailureOrAccountLockedFailure(
    id = InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_UNEXPECTED,
    context = context,
    onRetry = onRetry,
    onContactSupport = onContactSupport
  )

  fun AccountLockedFailure(
    context: AuthKeyRotationEventTrackerScreenIdContext,
    onRetry: () -> Unit,
    onContactSupport: () -> Unit,
  ) = UnexpectedFailureOrAccountLockedFailure(
    id = InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_ACCOUNT_LOCKED,
    context = context,
    onRetry = onRetry,
    onContactSupport = onContactSupport
  )

  private fun UnexpectedFailureOrAccountLockedFailure(
    id: EventTrackerScreenId,
    context: AuthKeyRotationEventTrackerScreenIdContext,
    onRetry: () -> Unit,
    onContactSupport: () -> Unit,
  ) = FormBodyModel(
    id = id,
    eventTrackerContext = context,
    onBack = null,
    toolbar = ToolbarModel(),
    header =
      FormHeaderModel(
        icon = Icon.LargeIconWarningFilled,
        headline = "Account out of sync",
        subline =
          """
            There was a problem connecting to Bitkey services, and your account may be out of sync,
            please try again.
            
            If that still doesn't work, you may need to recover another way, using your Bitkey
            hardware － try reaching out to support.
          """.trimIndent()
      ),
    primaryButton =
      ButtonModel(
        "Try again",
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onRetry)
      ),
    secondaryButton =
      ButtonModel(
        leadingIcon = Icon.SmallIconArrowUpRight,
        text = "Customer support",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick =
          StandardClick { onContactSupport() }
      )
  )

  fun RotatingKeys(context: AuthKeyRotationEventTrackerScreenIdContext) =
    LoadingBodyModel(
      message = "Removing all other devices...",
      id = InactiveAppEventTrackerScreenId.ROTATING_AUTH,
      eventTrackerContext = context
    )

  fun DismissingProposal(context: AuthKeyRotationEventTrackerScreenIdContext) =
    LoadingBodyModel(
      message = "",
      id = InactiveAppEventTrackerScreenId.DISMISS_ROTATION_PROPOSAL,
      eventTrackerContext = context
    )

  fun DeactivateDevicesAfterRestoreChoice(
    onNotRightNow: () -> Unit,
    removeAllOtherDevicesEnabled: Boolean,
    onRemoveAllOtherDevices: () -> Unit,
  ) = FormBodyModel(
    id = InactiveAppEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH,
    eventTrackerContext = AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION,
    onBack = null,
    toolbar = ToolbarModel(),
    header =
      FormHeaderModel(
        headline = "Remove all other devices",
        subline = "If you've restored a wallet, you might still be signed into Bitkey on another device. " +
          "You can remove Bitkey from other devices now, or choose to do this later from settings."
      ),
    secondaryButton =
      ButtonModel.BitkeyInteractionButtonModel(
        "Remove all other devices",
        size = ButtonModel.Size.Footer,
        isEnabled = removeAllOtherDevicesEnabled,
        onClick = StandardClick(onRemoveAllOtherDevices)
      ),
    primaryButton =
      ButtonModel(
        "Not right now",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onNotRightNow)
      )
  )

  fun DeactivateDevicesFromSettingsChoice(
    onBack: () -> Unit,
    removeAllOtherDevicesEnabled: Boolean,
    onRemoveAllOtherDevices: () -> Unit,
  ) = FormBodyModel(
    id = InactiveAppEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH,
    eventTrackerContext = AuthKeyRotationEventTrackerScreenIdContext.SETTINGS,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onClick = onBack)
    ),
    header =
      FormHeaderModel(
        headline = "Remove all other devices",
        subline = "If you’ve restored a wallet, your Bitkey might still be connected to another mobile device. " +
          "You can remove Bitkey from other mobile devices while continuing to use this one."
      ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      "Remove all other devices",
      isEnabled = removeAllOtherDevicesEnabled,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onRemoveAllOtherDevices)
    )
  )
}
