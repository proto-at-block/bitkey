package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.partnerships.PartnershipTransactionType
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.MONO
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.REGULAR
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.StepperIndicator
import build.wallet.statemachine.core.form.FormMainContentModel.StepperIndicator.StepStyle
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

data class TransactionDetailModel(
  val feeBumpEnabled: Boolean,
  val formHeaderModel: FormHeaderModel,
  val isLoading: Boolean,
  val viewTransactionText: String?,
  val onViewTransaction: () -> Unit,
  val onClose: () -> Unit,
  val onSpeedUpTransaction: () -> Unit,
  val content: ImmutableList<FormMainContentModel>,
) : FormBodyModel(
    primaryButton = viewTransactionText?.let {
      ButtonModel(
        leadingIcon = SmallIconArrowUpRight,
        text = viewTransactionText,
        size = Footer,
        onClick = StandardClick(onViewTransaction)
      )
    },
    secondaryButton = ButtonModel(
      leadingIcon = SmallIconLightning,
      text = "Speed Up",
      treatment = ButtonModel.Treatment.Secondary,
      size = Footer,
      isLoading = isLoading,
      onClick = StandardClick(onSpeedUpTransaction)
    ).takeIf { feeBumpEnabled },
    onBack = onClose,
    onSwipeToDismiss = onClose,
    header = formHeaderModel,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onClose)
    ),
    mainContentList = content,
    id = MoneyHomeEventTrackerScreenId.TRANSACTION_DETAIL
  )

/**
 * Generates the form header model for a pending transaction.
 */
fun pendingFormHeaderModel(
  isLate: Boolean,
  transaction: Transaction,
) = FormHeaderModel(
  iconModel = transaction.icon(),
  headline = if (isLate) "Transaction delayed" else transaction.pendingTitle(),
  sublineModel = StringModel(transaction.subtitle()),
  sublineTreatment = transaction.sublineTreatment(),
  alignment = LEADING
)

/**
 * Generates the form header model for a confirmed transaction.
 */
fun confirmedFormHeaderModel(transaction: Transaction) =
  FormHeaderModel(
    iconModel = transaction.icon(),
    headline = transaction.confirmedTitle(),
    subline = transaction.subtitle(),
    sublineTreatment = transaction.sublineTreatment(),
    alignment = LEADING
  )

private fun Transaction.sublineTreatment() =
  when (this) {
    is Transaction.BitcoinWalletTransaction -> MONO
    is Transaction.PartnershipTransaction -> REGULAR
  }

private fun Transaction.pendingTitle(): String =
  when (this) {
    is Transaction.BitcoinWalletTransaction -> when (details.transactionType) {
      Incoming, Outgoing -> "Transaction pending"
      UtxoConsolidation -> "Consolidation pending"
    }
    is Transaction.PartnershipTransaction -> title()
  }

private fun Transaction.confirmedTitle(): String {
  return when (this) {
    is Transaction.BitcoinWalletTransaction -> when (details.transactionType) {
      Incoming -> "Transaction received"
      Outgoing -> "Transaction sent"
      UtxoConsolidation -> "UTXO Consolidation"
    }
    is Transaction.PartnershipTransaction -> title()
  }
}

private fun Transaction.icon(): IconModel =
  when (this) {
    is Transaction.BitcoinWalletTransaction -> IconModel(
      icon = Bitcoin,
      iconSize = Avatar
    )
    is Transaction.PartnershipTransaction -> IconModel(
      iconImage = when (val url = details.partnerInfo.logoUrl) {
        null -> LocalImage(Bitcoin)
        else -> UrlImage(
          url = url,
          fallbackIcon = Bitcoin
        )
      },
      iconSize = Avatar
    )
  }

private fun Transaction.subtitle(): String =
  when (this) {
    is Transaction.BitcoinWalletTransaction -> details.chunkedRecipientAddress()
    is Transaction.PartnershipTransaction -> "Arrival times and fees are estimates. Confirm details through ${details.partnerInfo.name}."
  }

private fun Transaction.PartnershipTransaction.title() =
  when (details.type) {
    PartnershipTransactionType.PURCHASE -> "${details.partnerInfo.name} purchase"
    PartnershipTransactionType.TRANSFER -> "${details.partnerInfo.name} transfer"
    PartnershipTransactionType.SALE -> "${details.partnerInfo.name} sale"
  }

/**
 * A pre-built stepper indicator for a transaction in the submitted state.
 */
val submittedTransactionStepper: StepperIndicator = StepperIndicator(
  steps = immutableListOf(
    StepperIndicator.Step(
      style = StepStyle.COMPLETED,
      icon = LocalImage(icon = SmallIconCheck),
      label = "Submitted"
    ),
    StepperIndicator.Step(
      style = StepStyle.UPCOMING,
      icon = null,
      label = "Processing"
    ),
    StepperIndicator.Step(
      style = StepStyle.UPCOMING,
      icon = null,
      label = "Complete"
    )
  )
)

/**
 * A pre-built stepper indicator for a transaction in the processing state.
 */
val processingTransactionStepper: StepperIndicator = StepperIndicator(
  steps = immutableListOf(
    StepperIndicator.Step(
      style = StepStyle.COMPLETED,
      icon = LocalImage(icon = SmallIconCheck),
      label = "Submitted"
    ),
    StepperIndicator.Step(
      style = StepStyle.PENDING,
      icon = IconImage.LoadingBadge,
      label = "Processing"
    ),
    StepperIndicator.Step(
      style = StepStyle.UPCOMING,
      icon = null,
      label = "Complete"
    )
  )
)

/**
 * A pre-built stepper indicator for a completed transaction.
 */
val completeTransactionStepper: StepperIndicator = StepperIndicator(
  steps = immutableListOf(
    StepperIndicator.Step(
      style = StepStyle.COMPLETED,
      icon = LocalImage(icon = SmallIconCheck),
      label = "Submitted"
    ),
    StepperIndicator.Step(
      style = StepStyle.COMPLETED,
      icon = LocalImage(icon = SmallIconCheck),
      label = "Processing"
    ),
    StepperIndicator.Step(
      style = StepStyle.COMPLETED,
      icon = LocalImage(icon = SmallIconCheck),
      label = "Complete"
    )
  )
)
