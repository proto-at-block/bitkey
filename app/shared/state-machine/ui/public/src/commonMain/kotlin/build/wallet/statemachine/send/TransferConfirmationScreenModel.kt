package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.send.TransferConfirmationScreenVariant.Regular
import build.wallet.statemachine.send.TransferConfirmationScreenVariant.Sell
import build.wallet.statemachine.send.TransferConfirmationScreenVariant.SpeedUp
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A function which creates the form screen model for the transfer confirmation screen
 *
 * @param onBack - Handler for back press
 * @param onCancel - Handler for cancelling the transaction
 * @param variant - The variant of the transfer confirmation screen (Regular, SpeedUp, Sale)
 * @param recipientAddress - The recipient address of the transaction represented as a string
 * @param partnerName - The name of the partner exchange (used when variant is Sale)
 * @param transactionDetails [TransactionDetailsModel] - The data associated with the transaction
 * @param requiresHardware - Flag representing if the transaction requires hardware signing
 * @param confirmButtonEnabled - Flag representing if the primary button is enabled
 * @param errorOverlayModel - The model for displaying error overlays or sheets
 * @param onConfirmClick - Handler invoked when the primary button is clicked
 * @param onNetworkFeesClick - Handler for network fees click (null when disabled)
 * @param onArrivalTimeClick - Handler for arrival time click (null when disabled)
 */
fun TransferConfirmationScreenModel(
  onBack: () -> Unit,
  onCancel: () -> Unit,
  variant: TransferConfirmationScreenVariant,
  recipientAddress: String,
  transactionDetails: TransactionDetailsModel,
  requiresHardware: Boolean,
  confirmButtonEnabled: Boolean,
  errorOverlayModel: SheetModel? = null,
  onConfirmClick: () -> Unit,
  onNetworkFeesClick: () -> Unit,
  onArrivalTimeClick: (() -> Unit)?,
) = ScreenModel(
  body = TransferConfirmationScreenBodyModel(
    onBack = onBack,
    onCancel = onCancel,
    variant = variant,
    recipientAddress = recipientAddress,
    transactionDetails = transactionDetails,
    requiresHardware = requiresHardware,
    confirmButtonEnabled = confirmButtonEnabled,
    onConfirmClick = onConfirmClick,
    onNetworkFeesClick = onNetworkFeesClick,
    onArrivalTimeClick = onArrivalTimeClick
  ),
  presentationStyle = ScreenPresentationStyle.ModalFullScreen,
  bottomSheetModel = errorOverlayModel
)

private data class TransferConfirmationScreenBodyModel(
  override val onBack: () -> Unit,
  val onCancel: () -> Unit,
  val variant: TransferConfirmationScreenVariant,
  val recipientAddress: String,
  val transactionDetails: TransactionDetailsModel,
  val requiresHardware: Boolean,
  val confirmButtonEnabled: Boolean,
  val onConfirmClick: () -> Unit,
  val onNetworkFeesClick: () -> Unit,
  val onArrivalTimeClick: (() -> Unit)?,
) : FormBodyModel(
    onBack = onBack,
    header =
      when (variant) {
        Regular ->
          FormHeaderModel(
            icon = Bitcoin,
            headline = "Send your transfer",
            subline = recipientAddress,
            sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
            alignment = CENTER
          )
        SpeedUp ->
          FormHeaderModel(
            icon = LargeIconSpeedometer,
            headline = "Speed up your transfer to",
            subline = recipientAddress,
            sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
            alignment = CENTER
          )
        is Sell ->
          FormHeaderModel(
            icon = Bitcoin,
            headline = "Confirm sale to ${variant.partnerName}",
            subline = "Arrival times and fees are estimates.",
            alignment = CENTER
          )
      },
    toolbar =
      ToolbarModel(
        leadingAccessory =
          ToolbarAccessoryModel.ButtonAccessory(
            model =
              ButtonModel(
                text = "Cancel",
                treatment = TertiaryDestructive,
                size = Compact,
                onClick = StandardClick { onCancel() }
              )
          )
      ),
    mainContentList =
      transactionDetails.toDataList(
        variant = variant,
        onNetworkFeesClick = onNetworkFeesClick,
        onArrivalTimeClick = onArrivalTimeClick
      ),
    ctaWarning = null,
    primaryButton =
      ButtonModel(
        text = when (variant) {
          is Sell -> "Confirm"
          else -> "Send"
        },
        requiresBitkeyInteraction = requiresHardware,
        onClick = onConfirmClick,
        isEnabled = confirmButtonEnabled,
        treatment = Primary,
        size = Footer
      ),
    id = SendEventTrackerScreenId.SEND_CONFIRMATION,
    eventTrackerShouldTrack = false
  )

data class NetworkFeesInfoSheetModel(
  override val onBack: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Network Fees")
    ),
    header = null,
    primaryButton = ButtonModel(
      text = "Got it",
      size = Footer,
      onClick = SheetClosingClick(onBack)
    ),
    mainContentList = immutableListOf(
      Explainer(
        items = immutableListOf(
          Statement(
            title = null,
            body = FEES_EXPLAINER
          )
        )
      )
    ),
    renderContext = Sheet,
    id = SendEventTrackerScreenId.SEND_NETWORK_FEES_INFO_SHEET,
    eventTrackerShouldTrack = false
  )

data class FeeSelectionSheetModel(
  override val onBack: () -> Unit,
  val feeOptionList: FeeOptionList,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Transfer speed")
    ),
    header = null,
    primaryButton = null,
    mainContentList = immutableListOf(feeOptionList),
    renderContext = Sheet,
    id = SendEventTrackerScreenId.SEND_FEES_SELECTION_SHEET,
    eventTrackerShouldTrack = false
  )

private val FEES_EXPLAINER =
  """
  Network fees are small amounts paid by users when sending bitcoin transactions.
  
  These fees compensate miners for validating and adding transactions to the blockchain. The higher the fee, the more likely your transaction will be processed quickly, as miners prioritize transactions with higher fees.
  
  It’s important to note that these fees do not go to wallet providers. They are solely for supporting the Bitcoin network.
  """.trimIndent()

private fun TransactionDetailsModel.toDataList(
  variant: TransferConfirmationScreenVariant,
  onNetworkFeesClick: (() -> Unit)? = null,
  onArrivalTimeClick: (() -> Unit)?,
): ImmutableList<DataList> {
  val mainItems: ImmutableList<DataList.Data> =
    when (transactionDetailModelType) {
      is TransactionDetailModelType.Regular ->
        immutableListOf(
          DataList.Data(
            title = "Recipient receives",
            sideText = transactionDetailModelType.transferAmountText,
            secondarySideText = transactionDetailModelType.transferAmountSecondaryText
          ),
          DataList.Data(
            title = "Network Fees",
            onTitle = onNetworkFeesClick,
            titleIcon =
              IconModel(
                icon = SmallIconInformationFilled,
                iconSize = IconSize.XSmall,
                iconTint = IconTint.On30
              ),
            sideText = transactionDetailModelType.feeAmountText,
            secondarySideText = transactionDetailModelType.feeAmountSecondaryText
          )
        )
      is TransactionDetailModelType.SpeedUp ->
        immutableListOf(
          DataList.Data(
            title = "Recipient receives",
            sideText = transactionDetailModelType.transferAmountText,
            secondarySideText = transactionDetailModelType.transferAmountSecondaryText
          ),
          DataList.Data(
            title = "Original network fee",
            onTitle = onNetworkFeesClick,
            sideText = transactionDetailModelType.oldFeeAmountText,
            secondarySideText = transactionDetailModelType.oldFeeAmountSecondaryText
          ),
          DataList.Data(
            title = "Speed up network fee",
            onTitle = onNetworkFeesClick,
            sideText = transactionDetailModelType.feeDifferenceText,
            secondarySideText = transactionDetailModelType.feeDifferenceSecondaryText
          )
        )
      is TransactionDetailModelType.Sell ->
        immutableListOf(
          DataList.Data(
            title = amountLabel,
            sideText = transactionDetailModelType.transferAmountText,
            secondarySideText = transactionDetailModelType.transferAmountSecondaryText
          ),
          DataList.Data(
            title = "Network Fees",
            onTitle = onNetworkFeesClick,
            titleIcon =
              IconModel(
                icon = SmallIconInformationFilled,
                iconSize = IconSize.XSmall,
                iconTint = IconTint.On30
              ),
            sideText = transactionDetailModelType.feeAmountText
          )
        )
    }

  val totalTitleText = when (variant) {
    Regular, SpeedUp -> "Total Cost"
    is Sell -> "Total"
  }
  val dataLists = mutableListOf<DataList>()

  dataLists.add(
    DataList(
      items = immutableListOf(
        DataList.Data(
          title = when (variant) {
            Regular -> "Arrival time"
            SpeedUp -> "New arrival time"
            is Sell -> "Est. arrival time"
          },
          sideText = transactionSpeedText,
          onClick = onArrivalTimeClick
        )
      )
    )
  )

  if (variant is Sell) {
    dataLists.add(
      DataList(
        items = immutableListOf(
          DataList.Data(
            title = "Send to",
            sideText = variant.partnerName
          )
        )
      )
    )
  }

  dataLists.add(
    DataList(
      items = mainItems,
      total = DataList.Data(
        title = totalTitleText,
        sideText = transactionDetailModelType.totalAmountPrimaryText,
        sideTextType = DataList.Data.SideTextType.BODY2BOLD,
        secondarySideText = transactionDetailModelType.totalAmountSecondaryText
      )
    )
  )

  return dataLists.toImmutableList()
}

sealed interface TransferConfirmationScreenVariant {
  /**
   * Transaction confirmation for the regular send flow.
   */
  data object Regular : TransferConfirmationScreenVariant

  /**
   * Transaction confirmation when trying to speed up a transaction.
   */
  data object SpeedUp : TransferConfirmationScreenVariant

  /**
   * Transaction confirmation for the sell flow.
   */
  data class Sell(
    val partnerName: String,
  ) : TransferConfirmationScreenVariant
}
