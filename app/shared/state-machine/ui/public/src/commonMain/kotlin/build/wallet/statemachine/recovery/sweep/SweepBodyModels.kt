package build.wallet.statemachine.recovery.sweep

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon.LargeIconCheckFilled
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun generatingPsbtsBodyModel(
  id: EventTrackerScreenId,
  onBack: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body =
    LoadingBodyModel(
      message = "Checking for funds...",
      id = id,
      onBack = onBack,
      eventTrackerShouldTrack = false
    )
)

fun generatePsbtsFailedScreenModel(
  id: EventTrackerScreenId,
  onPrimaryButtonClick: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body =
    ErrorFormBodyModel(
      title = "Error",
      subline = "We couldn’t load your existing funds",
      primaryButton = ButtonDataModel("OK", onClick = onPrimaryButtonClick),
      eventTrackerScreenId = id,
      eventTrackerShouldTrack = false
    )
)

fun sweepFundsPrompt(
  id: EventTrackerScreenId,
  recoveredFactor: PhysicalFactor,
  fee: MoneyAmountModel,
  onSubmit: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body =
    FormBodyModel(
      id = id,
      onBack = null,
      toolbar = ToolbarModel(),
      header =
        FormHeaderModel(
          icon = LargeIconCheckFilled,
          headline = "Finalize recovery",
          subline =
            """
            You’ll need to approve a transaction, including an estimated network fee of ${fee.primaryAmount} (${fee.secondaryAmount}).

            This fee doesn’t go to Bitkey, but is allocated to the Bitcoin miners who validate transactions.

            If this fee appears very high, the network may be very busy. You can try returning later when fees may be lower.
            """.trimIndent()
        ),
      primaryButton =
        ButtonModel(
          text = "Complete recovery",
          requiresBitkeyInteraction = recoveredFactor == App,
          treatment = Primary,
          onClick = onSubmit,
          size = Footer
        ),
      eventTrackerShouldTrack = false
    )
)

fun zeroBalancePrompt(
  id: EventTrackerScreenId,
  onDone: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body =
    FormBodyModel(
      id = id,
      onBack = onDone,
      toolbar =
        ToolbarModel(
          leadingAccessory = CloseAccessory(onClick = onDone)
        ),
      header =
        FormHeaderModel(
          headline = "No funds found",
          subline = "We didn’t find any funds to move, or the amount of funds are lower than the network fees required to move them"
        ),
      primaryButton =
        ButtonModel(
          text = "OK",
          onClick = StandardClick(onDone),
          size = Footer
        ),
      eventTrackerShouldTrack = false
    )
)

fun broadcastingScreenModel(
  id: EventTrackerScreenId,
  onBack: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body =
    LoadingBodyModel(
      message = "Recovering funds...",
      id = id,
      onBack = onBack,
      eventTrackerShouldTrack = false
    )
)

fun sweepSuccessScreenModel(
  id: EventTrackerScreenId,
  recoveredFactor: PhysicalFactor,
  onDone: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body =
    FormBodyModel(
      id = id,
      onBack = onDone,
      toolbar =
        ToolbarModel(
          leadingAccessory = CloseAccessory(onClick = onDone)
        ),
      header =
        FormHeaderModel(
          icon = LargeIconCheckFilled,
          headline =
            when (recoveredFactor) {
              App -> "Your mobile key recovery is complete!"
              Hardware -> "Success!"
            },
          subline =
            when (recoveredFactor) {
              App -> "You can now safely use this phone to manage your Bitkey."
              Hardware -> "Your recovery is now complete and your new Bitkey device is ready to use"
            }
        ),
      primaryButton =
        ButtonModel(
          text = "Got it",
          onClick = StandardClick { onDone() },
          size = Footer
        ),
      eventTrackerShouldTrack = false
    )
)

fun sweepFailedScreenModel(
  id: EventTrackerScreenId,
  presentationStyle: ScreenPresentationStyle,
  onRetry: () -> Unit,
  onExit: () -> Unit,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body =
    FormBodyModel(
      id = id,
      onBack = onExit,
      toolbar =
        ToolbarModel(
          leadingAccessory = CloseAccessory(onClick = onExit)
        ),
      header =
        FormHeaderModel(
          headline = "The transactions failed to broadcast"
        ),
      primaryButton =
        ButtonModel(
          text = "Try again",
          onClick = StandardClick { onRetry() },
          size = Footer
        ),
      secondaryButton =
        ButtonModel(
          text = "Cancel",
          onClick = StandardClick { onExit() },
          size = Footer
        ),
      eventTrackerShouldTrack = false
    )
)
