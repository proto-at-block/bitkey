@file:Suppress("detekt:TooManyFunctions")

package build.wallet.ui.app.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.Icon.SmallIconCopy
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.MONO
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.transactions.TransactionDetailModel
import build.wallet.statemachine.transactions.completeTransactionStepper
import build.wallet.statemachine.transactions.processingTransactionStepper
import build.wallet.statemachine.transactions.submittedTransactionStepper
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.tooling.PreviewWalletTheme

private const val BITCOIN_ADDRESS = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"
private const val TRANSACTION_ID = "c4f5...67be"
private const val PARTNERSHIP_SUBLINE =
  "Arrival times and fees are estimates. Confirm details through Cash App."

@Preview(name = "Pending Receive")
@Composable
fun PendingReceiveTransactionDetailPreview() {
  TransactionDetailPreview(model = pendingReceiveTransactionDetailModel())
}

@Preview(name = "Pending Receive DSV2")
@Composable
fun PendingReceiveTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = pendingReceiveTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Late Send")
@Composable
fun LateSendTransactionDetailPreview() {
  TransactionDetailPreview(model = lateSendTransactionDetailModel())
}

@Preview(name = "Late Send DSV2")
@Composable
fun LateSendTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = lateSendTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Sent")
@Composable
fun SentTransactionDetailPreview() {
  TransactionDetailPreview(model = sentTransactionDetailModel())
}

@Preview(name = "Sent DSV2")
@Composable
fun SentTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = sentTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Received")
@Composable
fun ReceivedTransactionDetailPreview() {
  TransactionDetailPreview(model = receivedTransactionDetailModel())
}

@Preview(name = "Received DSV2")
@Composable
fun ReceivedTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = receivedTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "UTXO Consolidation")
@Composable
fun UtxoConsolidationTransactionDetailPreview() {
  TransactionDetailPreview(model = utxoConsolidationTransactionDetailModel())
}

@Preview(name = "UTXO Consolidation DSV2")
@Composable
fun UtxoConsolidationTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = utxoConsolidationTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Pending Partnership Transfer")
@Composable
fun PendingPartnershipTransactionDetailPreview() {
  TransactionDetailPreview(model = pendingPartnershipTransactionDetailModel())
}

@Preview(name = "Pending Partnership Transfer DSV2")
@Composable
fun PendingPartnershipTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = pendingPartnershipTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Pending Partnership Sale")
@Composable
fun PendingPartnershipSaleTransactionDetailPreview() {
  TransactionDetailPreview(model = pendingPartnershipSaleTransactionDetailModel())
}

@Preview(name = "Pending Partnership Sale DSV2")
@Composable
fun PendingPartnershipSaleTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = pendingPartnershipSaleTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Confirmed Partnership Sale")
@Composable
fun ConfirmedPartnershipTransactionDetailPreview() {
  TransactionDetailPreview(model = confirmedPartnershipTransactionDetailModel())
}

@Preview(name = "Confirmed Partnership Sale DSV2")
@Composable
fun ConfirmedPartnershipTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = confirmedPartnershipTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Confirmed Partnership Purchase")
@Composable
fun ConfirmedPartnershipPurchaseTransactionDetailPreview() {
  TransactionDetailPreview(model = confirmedPartnershipPurchaseTransactionDetailModel())
}

@Preview(name = "Confirmed Partnership Purchase DSV2")
@Composable
fun ConfirmedPartnershipPurchaseTransactionDetailDesignSystemV2Preview() {
  TransactionDetailPreview(
    model = confirmedPartnershipPurchaseTransactionDetailModel(),
    designSystemUpdatesEnabled = true
  )
}

@Composable
private fun TransactionDetailPreview(
  model: TransactionDetailModel,
  designSystemUpdatesEnabled: Boolean = false,
) {
  PreviewWalletTheme(designSystemUpdatesEnabled = designSystemUpdatesEnabled) {
    TransactionDetailScreen(model = model)
  }
}

private fun pendingReceiveTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = bitcoinHeader(headline = "Transaction pending"),
    isLoading = false,
    viewTransactionText = "View transaction",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      processingTransactionStepper,
      FormMainContentModel.Divider,
      transactionIdDataList(),
      DataList(
        items = immutableListOf(
          Data(
            title = "Amount",
            sideText = "$5.08",
            secondarySideText = "12,759 sats"
          ).asTransactionDetailTypography()
        )
      )
    )
  )

private fun lateSendTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = true,
    formHeaderModel = bitcoinHeader(headline = "Transaction delayed"),
    isLoading = false,
    viewTransactionText = "View transaction",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      processingTransactionStepper,
      FormMainContentModel.Divider,
      DataList(
        items = immutableListOf(
          Data(
            title = "Arrival time",
            sideText = "Feb 1 at 5:25pm",
            sideTextTreatment = Data.SideTextTreatment.STRIKETHROUGH,
            sideTextType = Data.SideTextType.REGULAR,
            secondarySideText = "7m late",
            secondarySideTextType = Data.SideTextType.BOLD,
            secondarySideTextTreatment = Data.SideTextTreatment.WARNING,
            explainer = Data.Explainer(
              title = "Speed up transaction?",
              subtitle = "You can speed up this transaction by increasing the network fee.",
              showTopDivider = true,
              iconButton = IconButtonModel(
                iconModel = IconModel(
                  icon = Icon.SmallIconInformationFilled,
                  iconSize = IconSize.Accessory,
                  iconBackgroundType = IconBackgroundType.Circle(
                    circleSize = IconSize.Accessory
                  ),
                  iconTint = IconTint.Foreground,
                  iconOpacity = 0.20f
                ),
                onClick = StandardClick { }
              )
            )
          ).asTransactionDetailTypography()
        )
      ),
      transactionIdDataList(),
      outgoingAmountDataList(
        amount = "$30.82",
        amountSats = "50,000 sats",
        fee = "$0.12",
        feeSats = "189 sats",
        total = "$5.08",
        totalSats = "12,759 sats"
      )
    )
  )

private fun sentTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = bitcoinHeader(headline = "Transaction sent"),
    isLoading = false,
    viewTransactionText = "View transaction",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      completeTransactionStepper,
      FormMainContentModel.Divider,
      confirmedDataList(),
      transactionIdDataList(),
      outgoingAmountDataList(
        amount = "$9.00",
        amountSats = "35,584 sats",
        fee = "$1.00",
        feeSats = "5,526 sats",
        total = "$10.00",
        totalSats = "41,110 sats"
      )
    )
  )

private fun receivedTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = bitcoinHeader(headline = "Transaction received"),
    isLoading = false,
    viewTransactionText = "View transaction",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      completeTransactionStepper,
      FormMainContentModel.Divider,
      confirmedDataList(),
      transactionIdDataList(),
      DataList(
        items = immutableListOf(
          Data(
            title = "Amount",
            sideText = "$10.00",
            secondarySideText = "41,110 sats"
          ).asTransactionDetailTypography()
        )
      )
    )
  )

private fun utxoConsolidationTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = bitcoinHeader(headline = "UTXO Consolidation"),
    isLoading = false,
    viewTransactionText = "View transaction",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      completeTransactionStepper,
      FormMainContentModel.Divider,
      confirmedDataList(sideText = "Sep 20 at 1:28 pm"),
      transactionIdDataList(),
      DataList(
        items = immutableListOf(
          Data(
            title = "UTXOs consolidated",
            sideText = "2 → 1"
          ).asTransactionDetailTypography(),
          Data(
            title = "Consolidation cost",
            sideText = "$1.23",
            secondarySideText = "2000 sats"
          ).asTransactionDetailTypography()
        ),
        total = null
      )
    )
  )

private fun pendingPartnershipTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = partnershipHeader(headline = "Cash App transfer"),
    isLoading = false,
    viewTransactionText = "View in Cash App",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      submittedTransactionStepper,
      FormMainContentModel.Divider,
      DataList(
        items = immutableListOf(
          Data(
            title = "Amount",
            sideText = "$5.08",
            secondarySideText = "12,759 sats"
          ).asTransactionDetailTypography()
        )
      )
    )
  )

private fun pendingPartnershipSaleTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = partnershipHeader(headline = "Cash App sale"),
    isLoading = false,
    viewTransactionText = "View in Cash App",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      submittedTransactionStepper,
      FormMainContentModel.Divider,
      DataList(
        items = immutableListOf(
          Data(
            title = "Amount",
            sideText = "$5.08",
            secondarySideText = "12,759 sats"
          ).asTransactionDetailTypography()
        )
      )
    )
  )

private fun confirmedPartnershipTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = partnershipHeader(headline = "Cash App sale"),
    isLoading = false,
    viewTransactionText = "View in Cash App",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      completeTransactionStepper,
      FormMainContentModel.Divider,
      confirmedDataList(),
      transactionIdDataList(),
      outgoingAmountDataList(
        amount = "$9.00",
        amountSats = "35,584 sats",
        fee = "$1.00",
        feeSats = "5,526 sats",
        total = "$10.00",
        totalSats = "41,110 sats"
      )
    )
  )

private fun confirmedPartnershipPurchaseTransactionDetailModel() =
  TransactionDetailModel(
    feeBumpEnabled = false,
    formHeaderModel = partnershipHeader(headline = "Cash App purchase"),
    isLoading = false,
    viewTransactionText = "View in Cash App",
    onViewTransaction = {},
    onClose = {},
    onSpeedUpTransaction = {},
    content = immutableListOf(
      completeTransactionStepper,
      FormMainContentModel.Divider,
      confirmedDataList(),
      transactionIdDataList(),
      outgoingAmountDataList(
        amount = "$9.00",
        amountSats = "35,584 sats",
        fee = "$1.00",
        feeSats = "5,526 sats",
        total = "$10.00",
        totalSats = "41,110 sats"
      )
    )
  )

private fun bitcoinHeader(headline: String) =
  FormHeaderModel(
    iconModel = IconModel(
      icon = Bitcoin,
      iconSize = Avatar
    ),
    headline = headline,
    sublineModel = StringModel(BITCOIN_ADDRESS),
    sublineTreatment = MONO,
    alignment = LEADING
  )

private fun partnershipHeader(headline: String) =
  FormHeaderModel(
    iconModel = IconModel(
      icon = Bitcoin,
      iconSize = Avatar
    ),
    headline = headline,
    sublineModel = StringModel(PARTNERSHIP_SUBLINE),
    alignment = LEADING
  )

private fun confirmedDataList(sideText: String = "03-17-1963") =
  DataList(
    items = immutableListOf(
      Data(
        title = "Confirmed",
        sideText = sideText
      ).asTransactionDetailTypography()
    )
  )

private fun transactionIdDataList() =
  DataList(
    items = immutableListOf(
      Data(
        title = "Transaction ID",
        sideText = TRANSACTION_ID,
        onClick = {},
        endIcon = SmallIconCopy
      ).asTransactionDetailTypography()
    )
  )

private fun outgoingAmountDataList(
  amount: String,
  amountSats: String,
  fee: String,
  feeSats: String,
  total: String,
  totalSats: String,
) = DataList(
  items = immutableListOf(
    Data(
      title = "Amount",
      sideText = amount,
      secondarySideText = amountSats
    ).asTransactionDetailTypography(),
    Data(
      title = "Network fees",
      sideText = fee,
      secondarySideText = feeSats
    ).asTransactionDetailTypography()
  ),
  total = Data(
    title = "Total",
    sideText = total,
    sideTextType = Data.SideTextType.BODY2BOLD,
    secondarySideText = totalSats
  ).asTransactionDetailTypography(
    titleTextType = Data.TitleTextType.BODY1REGULAR,
    sideTextType = Data.SideTextType.BODY1REGULAR,
    secondarySideTextType = Data.SideTextType.BODY2REGULAR
  )
)

private fun Data.asTransactionDetailTypography(
  titleTextType: Data.TitleTextType = Data.TitleTextType.BODY2REGULAR,
  sideTextType: Data.SideTextType = Data.SideTextType.BODY2REGULAR,
  secondarySideTextType: Data.SideTextType =
    if (secondarySideText != null) {
      Data.SideTextType.BODY2REGULAR
    } else {
      this.secondarySideTextType
    },
) = copy(
  titleTextType = titleTextType,
  sideTextType = sideTextType,
  secondarySideTextType = secondarySideTextType
)
