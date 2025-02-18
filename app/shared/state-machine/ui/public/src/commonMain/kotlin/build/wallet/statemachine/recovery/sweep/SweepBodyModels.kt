package build.wallet.statemachine.recovery.sweep

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.LargeIconCheckFilled
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.Icon.SmallIconQuestionNoOutline
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.send.NetworkFeesInfoSheetModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun generatingPsbtsBodyModel(
  id: EventTrackerScreenId?,
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
  id: EventTrackerScreenId?,
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

/**
 * Instructions to show a user to help them avoid sending funds to an inactive wallet.
 */
private val walletUpdateExplainers = immutableListOf(
  FormMainContentModel.Explainer.Statement(
    leadingIcon = Icon.SmallIconCheckStroked,
    title = "Don't use saved addresses",
    body = "Copy a fresh receive address every time you transfer bitcoin to avoid sending funds to an old wallet."
  ),
  FormMainContentModel.Explainer.Statement(
    leadingIcon = Icon.SmallIconMessage,
    title = "Alert contacts",
    body = "Make sure your contacts know to ask for a current wallet address every time they send you bitcoin."
  )
)

fun sweepInactiveHelpModel(
  id: EventTrackerScreenId?,
  presentationStyle: ScreenPresentationStyle,
  onLearnMore: () -> Unit,
  onBack: () -> Unit,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body = SweepInactiveHelpBodyModel(
    id = id,
    onBack = onBack,
    onLearnMore = onLearnMore
  )
)

private data class SweepInactiveHelpBodyModel(
  override val id: EventTrackerScreenId?,
  val onLearnMore: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = id,
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory = CloseAccessory(onClick = onBack)
      ),
    header =
      FormHeaderModel(
        headline = "Why did this happen?",
        subline = "When you recovered your Bitkey, your wallet address was updated. This deposit was sent to the old address."
      ),
    mainContentList = immutableListOf(
      FormMainContentModel.Explainer(
        items = walletUpdateExplainers
      )
    ),
    primaryButton = ButtonModel(
      text = "Learn more",
      treatment = ButtonModel.Treatment.Secondary,
      leadingIcon = Icon.SmallIconArrowUpRight,
      onClick = StandardClick { onLearnMore() },
      size = Footer
    )
  )

fun sweepFundsPrompt(
  id: EventTrackerScreenId?,
  recoveredFactor: PhysicalFactor?,
  transferAmount: MoneyAmountModel,
  fee: MoneyAmountModel,
  onShowNetworkFeesInfo: () -> Unit,
  onCloseNetworkFeesInfo: () -> Unit,
  showNetworkFeesInfoSheet: Boolean,
  onBack: (() -> Unit)?,
  onHelpClick: () -> Unit,
  onSubmit: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  bottomSheetModel = SheetModel(
    body = NetworkFeesInfoSheetModel(onBack = onCloseNetworkFeesInfo),
    onClosed = onCloseNetworkFeesInfo
  ).takeIf { showNetworkFeesInfoSheet },
  presentationStyle = presentationStyle,
  body = SweepFundsPromptBodyModel(
    id = id,
    recoveredFactor = recoveredFactor,
    transferAmount = transferAmount,
    fee = fee,
    onShowNetworkFeesInfo = onShowNetworkFeesInfo,
    onBack = onBack,
    onHelpClick = onHelpClick,
    onSubmit = onSubmit
  )
)

private data class SweepFundsPromptBodyModel(
  override val id: EventTrackerScreenId?,
  val recoveredFactor: PhysicalFactor?,
  val transferAmount: MoneyAmountModel,
  val fee: MoneyAmountModel,
  val onShowNetworkFeesInfo: () -> Unit,
  override val onBack: (() -> Unit)?,
  val onHelpClick: () -> Unit,
  val onSubmit: () -> Unit,
) : FormBodyModel(
    id = id,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = when (onBack) {
        null -> null
        else -> BackAccessory(onClick = onBack)
      },
      trailingAccessory = ToolbarAccessoryModel.IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = SmallIconQuestionNoOutline,
            iconSize = Accessory,
            iconBackgroundType = Circle(circleSize = Regular)
          ),
          onClick = StandardClick { onHelpClick() }
        )
      ).takeIf { recoveredFactor == null }
    ),
    header =
      FormHeaderModel(
        iconModel = IconModel(
          icon = when (recoveredFactor) {
            null -> LargeIconWarningFilled
            else -> LargeIconCheckFilled
          },
          iconSize = IconSize.Avatar,
          iconTint = when (recoveredFactor) {
            null -> IconTint.On30
            else -> IconTint.Primary
          }
        ),
        headline = when (recoveredFactor) {
          App, Hardware -> "Finalize recovery"
          null -> "Transfer funds to active wallet"
        },
        subline = when (recoveredFactor) {
          App, Hardware -> null
          null -> "These funds were deposited in an inactive wallet. Transfer funds to your active wallet and discontinue use of your old address."
        }
      ),
    mainContentList = immutableListOf(
      FormMainContentModel.DataList(
        items = immutableListOf(
          FormMainContentModel.DataList.Data(
            title = "Sent From",
            sideText = "Old wallet"
          )
        )
      ),
      FormMainContentModel.DataList(
        items = immutableListOf(
          FormMainContentModel.DataList.Data(
            title = "Amount to Transfer",
            sideText = transferAmount.secondaryAmount
          ),
          FormMainContentModel.DataList.Data(
            title = "Network fees",
            onTitle = onShowNetworkFeesInfo,
            titleIcon =
              IconModel(
                icon = Icon.SmallIconInformationFilled,
                iconSize = IconSize.XSmall,
                iconTint = IconTint.On30
              ),
            sideText = fee.secondaryAmount
          )
        ),
        total = FormMainContentModel.DataList.Data(
          title = "Total",
          sideText = transferAmount.secondaryAmount,
          secondarySideText = transferAmount.primaryAmount
        )
      )
    ),
    primaryButton =
      ButtonModel(
        text = when (recoveredFactor) {
          App, Hardware -> "Complete Recovery"
          null -> "Confirm Transfer"
        },
        requiresBitkeyInteraction = recoveredFactor == App,
        treatment = Primary,
        onClick = onSubmit,
        size = Footer
      ),
    eventTrackerShouldTrack = false
  )

fun zeroBalancePrompt(
  id: EventTrackerScreenId?,
  onDone: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body = ZeroBalancePromptBodyModel(
    id = id,
    onDone = onDone
  )
)

data class ZeroBalancePromptBodyModel(
  override val id: EventTrackerScreenId?,
  val onDone: () -> Unit,
) : FormBodyModel(
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

fun broadcastingScreenModel(
  id: EventTrackerScreenId?,
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
  id: EventTrackerScreenId?,
  recoveredFactor: PhysicalFactor?,
  onDone: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body = SweepSuccessScreenBodyModel(
    id = id,
    recoveredFactor = recoveredFactor,
    onDone = onDone
  )
)

private data class SweepSuccessScreenBodyModel(
  override val id: EventTrackerScreenId?,
  val recoveredFactor: PhysicalFactor?,
  val onDone: () -> Unit,
) : FormBodyModel(
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
            null -> "Your transfer is complete!"
          },
        subline =
          when (recoveredFactor) {
            App, null -> "You can now safely use this phone to manage your Bitkey. Take precautions to avoid sending money to your old wallet."
            Hardware -> "Your recovery is now complete and your new Bitkey device is ready to use. Take precautions to avoid sending money to your old wallet."
          }
      ),
    mainContentList = immutableListOf(
      FormMainContentModel.Explainer(
        items = walletUpdateExplainers
      )
    ),
    primaryButton =
      ButtonModel(
        text = "Got it",
        onClick = StandardClick { onDone() },
        size = Footer
      ),
    eventTrackerShouldTrack = false
  )

fun sweepFailedScreenModel(
  id: EventTrackerScreenId?,
  presentationStyle: ScreenPresentationStyle,
  errorData: ErrorData,
  onRetry: () -> Unit,
  onExit: () -> Unit,
) = ScreenModel(
  presentationStyle = presentationStyle,
  body = SweepFailedScreenBodyModel(
    id = id,
    errorData = errorData,
    onRetry = onRetry,
    onExit = onExit
  )
)

private data class SweepFailedScreenBodyModel(
  override val id: EventTrackerScreenId?,
  override val errorData: ErrorData,
  val onRetry: () -> Unit,
  val onExit: () -> Unit,
) : FormBodyModel(
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
    eventTrackerShouldTrack = false,
    errorData = errorData
  )
