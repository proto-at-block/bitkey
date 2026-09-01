package build.wallet.statemachine.transactions

import build.wallet.bitcoin.metadata.TransactionNote
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data

/**
 * Applies the standard transaction-detail typography to a [Data] row.
 */
internal fun Data.asTransactionDetailTypography(
  titleTextType: Data.TitleTextType = Data.TitleTextType.BODY2REGULAR,
  sideTextType: Data.SideTextType = Data.SideTextType.BODY2REGULAR,
  secondarySideTextType: Data.SideTextType =
    if (secondarySideText != null) {
      Data.SideTextType.BODY2REGULAR
    } else {
      this.secondarySideTextType
    },
): Data {
  return copy(
    titleTextType = titleTextType,
    sideTextType = sideTextType,
    secondarySideTextType = secondarySideTextType
  )
}

/**
 * The "Note" row on the transaction details screen. Shows the customer's note when one
 * exists, or an "Add a note" prompt otherwise; tapping opens the note edit sheet.
 */
internal fun transactionNoteDataList(
  transactionNote: TransactionNote?,
  onEditTransactionNote: () -> Unit,
): DataList =
  DataList(
    items = immutableListOf(
      Data(
        title = "Note",
        sideText = transactionNote?.note ?: "Add a note",
        sideTextTreatment = if (transactionNote == null) {
          Data.SideTextTreatment.SECONDARY
        } else {
          Data.SideTextTreatment.PRIMARY
        },
        onClick = onEditTransactionNote
      ).asTransactionDetailTypography()
    )
  )
