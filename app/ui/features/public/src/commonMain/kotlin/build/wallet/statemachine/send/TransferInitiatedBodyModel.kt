package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import kotlinx.collections.immutable.ImmutableList

/**
 * A function which creates the form screen model for the transfer initiated screen
 *
 * @param onBack - Handler for back press
 * @param recipientAddress - The recipient address of the transaction represented as a string
 * @param transactionDetails [TransactionDetailsModel] - The data associated with the transaction
 * @param onDone - Handler invoked once the primary button is clicked
 */
data class TransferInitiatedBodyModel(
  override val onBack: () -> Unit,
  val recipientAddress: BitcoinAddress,
  val transactionDetails: TransactionDetailsModel,
  val primaryButtonText: String = "Done",
  val eventTrackerScreenId: EventTrackerScreenId = SendEventTrackerScreenId.SEND_INITIATED_SUCCESS,
  val shouldTrack: Boolean = false,
  val onDone: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Transfer sent",
      subline = recipientAddress.chunkedAddress(),
      sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
      alignment = LEADING
    ),
    toolbar = null,
    mainContentList = transactionDetails.toFormContent(),
    primaryButton = ButtonModel(
      text = primaryButtonText,
      onClick = StandardClick(onDone),
      size = Footer
    ),
    id = eventTrackerScreenId,
    eventTrackerShouldTrack = shouldTrack
  )

private fun TransactionDetailsModel.toFormContent(): ImmutableList<FormMainContentModel> {
  val mainItems: ImmutableList<Data> =
    when (transactionDetailModelType) {
      is TransactionDetailModelType.Regular ->
        immutableListOf(
          Data(
            title = "Amount",
            sideText = transactionDetailModelType.transferAmountText,
            secondarySideText = transactionDetailModelType.transferAmountSecondaryText
          ),
          Data(
            title = "Network fees",
            sideText = transactionDetailModelType.feeAmountText,
            secondarySideText = transactionDetailModelType.feeAmountSecondaryText
          )
        )
      is TransactionDetailModelType.SpeedUp ->
        immutableListOf(
          Data(
            title = "Amount",
            sideText = transactionDetailModelType.transferAmountText,
            secondarySideText = transactionDetailModelType.transferAmountSecondaryText
          ),
          Data(
            title = "Original network fee",
            sideText = transactionDetailModelType.oldFeeAmountText,
            secondarySideText = transactionDetailModelType.oldFeeAmountSecondaryText
          ),
          Data(
            title = "Speed up network fee",
            sideText = transactionDetailModelType.feeDifferenceText,
            secondarySideText = transactionDetailModelType.feeDifferenceSecondaryText
          )
        )
    }

  return immutableListOf(
    FormMainContentModel.Divider,
    DataList(
      items =
        immutableListOf(
          Data(
            title = "Arrival time",
            sideText = transactionSpeedText
          )
        )
    ),
    DataList(
      items = mainItems,
      total =
        Data(
          title = "Total",
          sideText = transactionDetailModelType.totalAmountPrimaryText,
          sideTextType = Data.SideTextType.BODY2BOLD,
          secondarySideText = transactionDetailModelType.totalAmountSecondaryText
        )
    )
  )
}
