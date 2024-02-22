package build.wallet.statemachine.send

import build.wallet.ui.model.Model
import dev.zacsweers.redacted.annotations.Redacted

@Redacted
data class TransactionDetailsModel(
  val transactionDetailModelType: TransactionDetailModelType,
  val transactionSpeedText: String,
  val totalAmountPrimaryText: String,
  val totalAmountSecondaryText: String?,
) : Model()

/**
 * Transaction detail display type for transaction confirmation and initiated screens.
 *
 * @property transferAmountText the "Recipient receives" amount.
 */
sealed class TransactionDetailModelType {
  abstract val transferAmountText: String

  /**
   * For a "regular" bitcoin send/receive transaction.
   *
   * @property feeAmountText The fees paid for this transaction.
   */
  data class Regular(
    override val transferAmountText: String,
    val feeAmountText: String,
  ) : TransactionDetailModelType()

  /**
   * For a bitcoin send transaction to be sped up via replace-by-fee (RBF).
   *
   * @property oldFeeAmountText The initial fees paid for this transaction.
   * @property feeDifferenceText The cost difference to speed up the transaction.
   */
  data class SpeedUp(
    override val transferAmountText: String,
    val oldFeeAmountText: String,
    val feeDifferenceText: String,
  ) : TransactionDetailModelType()
}
