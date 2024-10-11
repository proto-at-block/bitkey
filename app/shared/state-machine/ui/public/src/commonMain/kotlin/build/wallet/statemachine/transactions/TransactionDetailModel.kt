package build.wallet.statemachine.transactions

import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.MONO
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconSize.Large
import build.wallet.ui.model.icon.IconTint.Foreground
import build.wallet.ui.model.icon.IconTint.Primary
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

data class TransactionDetailModel(
  val feeBumpEnabled: Boolean,
  val txStatusModel: TxStatusModel,
  val isLoading: Boolean,
  val onViewTransaction: () -> Unit,
  val onClose: () -> Unit,
  val onSpeedUpTransaction: () -> Unit,
  val content: ImmutableList<DataList>,
) : FormBodyModel(
    primaryButton = if (feeBumpEnabled) {
      ButtonModel(
        leadingIcon = SmallIconLightning,
        text = "Speed Up",
        treatment = ButtonModel.Treatment.Secondary,
        size = Footer,
        isLoading = isLoading,
        onClick = StandardClick(onSpeedUpTransaction)
      )
    } else {
      ButtonModel(
        leadingIcon = SmallIconArrowUpRight,
        text = "View Transaction",
        size = Footer,
        onClick = StandardClick(onViewTransaction)
      )
    },
    secondaryButton = if (feeBumpEnabled) {
      ButtonModel(
        leadingIcon = SmallIconArrowUpRight,
        text = "View Transaction",
        size = Footer,
        onClick = StandardClick(onViewTransaction)
      )
    } else {
      null
    },
    onBack = onClose,
    onSwipeToDismiss = onClose,
    header = txStatusModel.toFormHeaderModel(),
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onClose)
    ),
    mainContentList = content,
    id = MoneyHomeEventTrackerScreenId.TRANSACTION_DETAIL
  )

sealed interface TxStatusModel {
  val transactionType: TransactionType
  val recipientAddress: String

  fun toFormHeaderModel(): FormHeaderModel

  data class Pending(
    override val transactionType: TransactionType,
    override val recipientAddress: String,
    val isLate: Boolean,
  ) : TxStatusModel {
    override fun toFormHeaderModel(): FormHeaderModel =
      when {
        isLate -> FormHeaderModel(
          icon = LargeIconWarningFilled,
          headline = "Transaction delayed",
          subline = recipientAddress,
          sublineTreatment = MONO,
          alignment = CENTER
        )
        else -> FormHeaderModel(
          iconModel = IconModel(
            iconImage = IconImage.Loader,
            iconSize = IconSize.Large,
            iconBackgroundType = Circle(circleSize = Avatar)
          ),
          headline = when (transactionType) {
            Incoming, Outgoing -> "Transaction pending"
            UtxoConsolidation -> "Consolidation pending"
          },
          sublineModel = LabelModel.StringWithStyledSubstringModel.from(
            string = recipientAddress,
            substringToColor = emptyMap()
          ),
          sublineTreatment = MONO,
          alignment = CENTER
        )
      }
  }

  data class Confirmed(
    override val transactionType: TransactionType,
    override val recipientAddress: String,
  ) : TxStatusModel {
    override fun toFormHeaderModel(): FormHeaderModel =
      FormHeaderModel(
        iconModel = when (transactionType) {
          Incoming -> IncomingTransactionIconModel
          Outgoing -> OutgoingTransactionIconModel
          UtxoConsolidation -> UtxoConsolidationTransactionIconModel
        },
        headline = when (transactionType) {
          Incoming -> "Transaction received"
          Outgoing -> "Transaction sent"
          UtxoConsolidation -> "UTXO Consolidation"
        },
        subline = recipientAddress,
        sublineTreatment = MONO,
        alignment = CENTER
      )
  }
}

internal val OutgoingTransactionIconModel = IconModel(
  icon = LargeIconCheckFilled,
  iconSize = Avatar,
  iconTint = Primary
)

internal val IncomingTransactionIconModel = IconModel(
  icon = Bitcoin,
  iconSize = Avatar,
  iconTint = Primary
)

internal val UtxoConsolidationTransactionIconModel = IconModel(
  icon = SmallIconConsolidation,
  iconSize = Large,
  iconBackgroundType = Circle(circleSize = Avatar),
  iconTint = Foreground
)
