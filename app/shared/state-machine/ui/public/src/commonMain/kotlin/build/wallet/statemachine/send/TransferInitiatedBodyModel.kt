package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
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
fun TransferInitiatedBodyModel(
  onBack: () -> Unit,
  recipientAddress: String,
  transactionDetails: TransactionDetailsModel,
  onDone: () -> Unit,
) = FormBodyModel(
  onBack = onBack,
  header =
    FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Transfer sent",
      subline = recipientAddress,
      sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
      alignment = CENTER
    ),
  toolbar = null,
  mainContentList = transactionDetails.toDataList(),
  primaryButton =
    ButtonModel(
      text = "Done",
      onClick = StandardClick(onDone),
      size = Footer
    ),
  id = SendEventTrackerScreenId.SEND_INITIATED_SUCCESS,
  eventTrackerShouldTrack = false
)

private fun TransactionDetailsModel.toDataList(): ImmutableList<DataList> {
  val mainItems: ImmutableList<Data> =
    when (transactionDetailModelType) {
      is TransactionDetailModelType.Regular ->
        immutableListOf(
          Data(
            title = "Recipient receives",
            sideText = transactionDetailModelType.transferAmountText
          ),
          Data(
            title = "Network Fees",
            sideText = transactionDetailModelType.feeAmountText
          )
        )
      is TransactionDetailModelType.SpeedUp ->
        immutableListOf(
          Data(
            title = "Recipient receives",
            sideText = transactionDetailModelType.transferAmountText
          ),
          Data(
            title = "Original network fee",
            sideText = transactionDetailModelType.oldFeeAmountText
          ),
          Data(
            title = "Speed up network fee",
            sideText = transactionDetailModelType.feeDifferenceText
          )
        )
    }

  return immutableListOf(
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
          title = "Total Cost",
          sideText = transactionDetailModelType.totalAmountPrimaryText,
          sideTextType = Data.SideTextType.BODY2BOLD,
          secondarySideText = transactionDetailModelType.totalAmountSecondaryText
        )
    )
  )
}
