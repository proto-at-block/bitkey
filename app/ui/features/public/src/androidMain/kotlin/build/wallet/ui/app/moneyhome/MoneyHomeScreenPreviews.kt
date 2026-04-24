@file:Suppress("TooManyFunctions")

package build.wallet.ui.app.moneyhome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.pricechart.DataPoint
import build.wallet.pricechart.PriceDirection
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedTaskRowModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.transactions.PartnerTransactionItemModel
import build.wallet.statemachine.transactions.SkeletonTransactionItemModel
import build.wallet.statemachine.transactions.TransactionItemModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.BadgeType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.market.MarketIcons
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun MoneyHomeScreenFullPreview(
  hideBalance: Boolean = false,
  largeBalance: Boolean = false,
  showSellButton: Boolean = false,
) {
  PreviewWalletTheme {
    MoneyHomeScreenFull(
      hideBalance = hideBalance,
      largeBalance = largeBalance,
      showSellButton = showSellButton
    )
  }
}

@Preview(name = "Money Home DSV2 Light")
@Composable
fun MoneyHomeScreenFullDesignSystemV2PreviewLight() {
  PreviewWalletTheme(
    designSystemUpdatesEnabled = true
  ) {
    MoneyHomeScreenFullNewWalletGettingStartedNoActivity()
  }
}

@Preview(name = "Money Home DSV2 Dark")
@Composable
fun MoneyHomeScreenFullDesignSystemV2PreviewDark() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    designSystemUpdatesEnabled = true
  ) {
    MoneyHomeScreenFullNewWalletGettingStartedNoActivity()
  }
}

@Preview(name = "Money Home Pending Activity DSV2 Light")
@Composable
fun MoneyHomeScreenFullPendingActivityDesignSystemV2PreviewLight() {
  PreviewWalletTheme(
    designSystemUpdatesEnabled = true
  ) {
    MoneyHomeScreenFullWithPendingActivity()
  }
}

@Preview(name = "Money Home Pending Activity DSV2 Dark")
@Composable
fun MoneyHomeScreenFullPendingActivityDesignSystemV2PreviewDark() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    designSystemUpdatesEnabled = true
  ) {
    MoneyHomeScreenFullWithPendingActivity()
  }
}

@Composable
fun MoneyHomeScreenFull(
  hideBalance: Boolean = false,
  largeBalance: Boolean = false,
  showSellButton: Boolean = false,
  isBuyButtonEnabled: Boolean = false,
  isSellButtonEnabled: Boolean = false,
  useSatsForRecentActivity: Boolean = false,
  usePendingActivity: Boolean = false,
  securityHubBadged: Boolean = false,
  isLoading: Boolean = false,
  isLoadingTransactions: Boolean = false,
  useSkeletonTransactions: Boolean = false,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  MoneyHomeScreen(
    model =
      MoneyHomeBodyModel(
        onSettings = {},
        hideBalance = hideBalance,
        onHideBalance = {},
        balanceModel = moneyHomeBalanceModel(largeBalance = largeBalance, isLoading = isLoading),
        cardsModel = CardListModel(cards = emptyImmutableList()),
        transactionsModel = moneyHomeRecentActivityModel(
          useSatsForRecentActivity = useSatsForRecentActivity,
          usePendingActivity = usePendingActivity,
          isLoadingTransactions = isLoadingTransactions,
          useSkeletonTransactions = useSkeletonTransactions
        ),
        seeAllButtonModel =
          ButtonModel(
            "See All",
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Footer,
            onClick = StandardClick {}
          ),
        coachmark = null,
        buttonsModel = moneyMovementButtonsModel(
          showSellButton = showSellButton,
          isBuyButtonEnabled = isBuyButtonEnabled,
          isSellButtonEnabled = isSellButtonEnabled
        ),
        onRefresh = {},
        isRefreshing = false,
        onOpenPriceDetails = {},
        trailingToolbarAccessoryModel = moneyHomeToolbarAccessory(isDesignSystemV2Enabled),
        onSecurityHubTabClick = {},
        isSecurityHubBadged = securityHubBadged
      )
  )
}

private fun moneyHomeBalanceModel(
  largeBalance: Boolean,
  isLoading: Boolean,
) = if (largeBalance) {
  MoneyAmountModel(
    primaryAmount = "$88,888,888.88",
    secondaryAmount = "153,984,147,317 sats",
    isLoading = isLoading
  )
} else {
  MoneyAmountModel(
    primaryAmount = "$289,745",
    secondaryAmount = "424,567 sats",
    isLoading = isLoading
  )
}

private fun moneyHomeRecentActivityModel(
  useSatsForRecentActivity: Boolean,
  usePendingActivity: Boolean,
  isLoadingTransactions: Boolean,
  useSkeletonTransactions: Boolean,
) = ListModel(
  headerText = "Recent activity",
  sections =
    immutableListOf(
      ListGroupModel(
        header = null,
        style = ListGroupStyle.NONE,
        items = if (useSkeletonTransactions) {
          immutableListOf(
            SkeletonTransactionItemModel(),
            SkeletonTransactionItemModel(),
            SkeletonTransactionItemModel(),
            SkeletonTransactionItemModel()
          )
        } else {
          populatedRecentActivityItems(
            useSatsForRecentActivity = useSatsForRecentActivity,
            usePendingActivity = usePendingActivity,
            isLoadingTransactions = isLoadingTransactions
          )
        }
      )
    )
)

private fun populatedRecentActivityItems(
  useSatsForRecentActivity: Boolean,
  usePendingActivity: Boolean,
  isLoadingTransactions: Boolean,
) = immutableListOf(
  TransactionItemModel(
    truncatedRecipientAddress = "1AH7...CkGJ",
    date = "Pending",
    amount = "+ $11.36",
    amountEquivalent = if (useSatsForRecentActivity) "10,500 sats" else "0.000105 BTC",
    transactionType = Incoming,
    isPending = usePendingActivity,
    isLate = false,
    pendingBadgeType = BadgeType.Loading,
    isLoading = isLoadingTransactions,
    onClick = {}
  ),
  TransactionItemModel(
    truncatedRecipientAddress = "2AH7...CkGJ",
    date = if (usePendingActivity) "3 hours ago" else "Pending",
    amount = "$21.36",
    amountEquivalent = if (useSatsForRecentActivity) "20,500 sats" else "0.000205 BTC",
    transactionType = Outgoing,
    isPending = false,
    isLate = false,
    isLoading = isLoadingTransactions,
    onClick = {}
  ),
  TransactionItemModel(
    truncatedRecipientAddress = "3AH7...CkGJ",
    date = if (usePendingActivity) "July 4" else "Pending",
    amount = "$31.36",
    amountEquivalent = if (useSatsForRecentActivity) "30,500 sats" else "0.000305 BTC",
    transactionType = UtxoConsolidation,
    isPending = false,
    isLate = false,
    isLoading = isLoadingTransactions,
    onClick = {}
  ),
  if (usePendingActivity) {
    previewPartnerTransactionItemModel(
      title = "Purchase",
      date = "Pending",
      logoUrl = null,
      amount = null,
      amountEquivalent = null,
      isPending = true,
      isError = false,
      pendingBadgeType = BadgeType.Loading,
      sideTextTint = ListItemSideTextTint.GREEN,
      isLoading = isLoadingTransactions,
      onClick = {}
    )
  } else {
    PartnerTransactionItemModel(
      title = "Purchase",
      date = "July 4",
      logoUrl = null,
      amount = "$31.36",
      amountEquivalent = if (useSatsForRecentActivity) "30,500 sats" else "0.000305 BTC",
      isPending = false,
      isError = false,
      pendingBadgeType = BadgeType.Loading,
      sideTextTint = ListItemSideTextTint.GREEN,
      isLoading = isLoadingTransactions,
      onClick = {}
    )
  }
)

private fun previewPartnerIconImage() = IconImage.MarketIconImage(MarketIcons.CashAppMulticolor)

private fun previewPartnerTransactionItemModel(
  title: String,
  date: String,
  logoUrl: String?,
  amount: String?,
  amountEquivalent: String?,
  isPending: Boolean,
  isError: Boolean,
  pendingBadgeType: BadgeType = BadgeType.Loading,
  sideTextTint: ListItemSideTextTint,
  isLoading: Boolean = false,
  onClick: () -> Unit,
) = PartnerTransactionItemModel(
  title = title,
  date = date,
  logoUrl = logoUrl,
  amount = amount,
  amountEquivalent = amountEquivalent,
  isPending = isPending,
  isError = isError,
  pendingBadgeType = pendingBadgeType,
  sideTextTint = sideTextTint,
  isLoading = isLoading,
  onClick = onClick
).let { model ->
  val leadingAccessory = model.leadingAccessory as IconAccessory
  model.copy(
    leadingAccessory = leadingAccessory.copy(
      model = leadingAccessory.model.copy(iconImage = previewPartnerIconImage())
    )
  )
}

private fun moneyMovementButtonsModel(
  showSellButton: Boolean,
  isBuyButtonEnabled: Boolean,
  isSellButtonEnabled: Boolean,
) = MoneyHomeButtonsModel.MoneyMovementButtonsModel(
  addButton =
    MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
      enabled = isBuyButtonEnabled,
      onClick = {}
    ),
  sellButton = if (showSellButton) {
    MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
      enabled = isSellButtonEnabled,
      onClick = {}
    )
  } else {
    null
  },
  sendButton =
    MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
      enabled = true,
      onClick = {}
    ),
  receiveButton =
    MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
      enabled = true,
      onClick = {}
    )
)

private fun moneyHomeToolbarAccessory(isDesignSystemV2Enabled: Boolean) =
  ToolbarAccessoryModel.IconAccessory(
    model = IconButtonModel(
      iconModel = if (isDesignSystemV2Enabled) {
        IconModel(
          icon = MarketIcons.EllipsisHorizontal,
          iconSize = IconSize.HeaderToolbar,
          iconTint = IconTint.Foreground
        )
      } else {
        IconModel(
          icon = Icon.SmallIconSettingsBadged,
          iconSize = IconSize.HeaderToolbar
        )
      },
      onClick = StandardClick {}
    )
  )

@Preview
@Composable
fun MoneyHomeScreenFullWithBuyAndSellEnabledPreview() {
  PreviewWalletTheme {
    MoneyHomeScreenFullWithBuyAndSellEnabled()
  }
}

@Composable
fun MoneyHomeScreenFullWithBuyAndSellEnabled() {
  MoneyHomeScreenFull(
    showSellButton = true,
    isBuyButtonEnabled = true,
    isSellButtonEnabled = true,
    useSatsForRecentActivity = true
  )
}

@Composable
fun MoneyHomeScreenFullWithPendingActivity() {
  MoneyHomeScreenFull(
    usePendingActivity = true
  )
}

@Preview
@Composable
fun MoneyHomeScreenLitePreview() {
  PreviewWalletTheme {
    MoneyHomeScreenLite()
  }
}

@Preview(name = "Money Home Lite DSV2 Light")
@Composable
fun MoneyHomeScreenLiteDesignSystemV2PreviewLight() {
  PreviewWalletTheme(
    designSystemUpdatesEnabled = true
  ) {
    MoneyHomeScreenLite(isDesignSystemV2Enabled = true)
  }
}

@Preview(name = "Money Home Lite DSV2 Dark")
@Composable
fun MoneyHomeScreenLiteDesignSystemV2PreviewDark() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    designSystemUpdatesEnabled = true
  ) {
    MoneyHomeScreenLite(isDesignSystemV2Enabled = true)
  }
}

@Composable
fun MoneyHomeScreenLite(isDesignSystemV2Enabled: Boolean = false) {
  LiteMoneyHomeScreen(
    model =
      LiteMoneyHomeBodyModel(
        onSettings = {},
        buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
        protectedCustomers = immutableListOf(
          ProtectedCustomer(
            "",
            ProtectedCustomerAlias("Alice"),
            setOf(TrustedContactRole.SocialRecoveryContact)
          )
        ),
        onProtectedCustomerClick = {},
        onBuyOwnBitkeyClick = {},
        onAcceptInviteClick = {},
        onIHaveABitkeyClick = {},
        isDesignSystemV2Enabled = isDesignSystemV2Enabled
      )
  )
}

@Preview
@Composable
fun MoneyHomeScreenLiteWithoutProtectedCustomersPreview() {
  PreviewWalletTheme {
    MoneyHomeScreenLiteWithoutProtectedCustomers()
  }
}

@Composable
fun MoneyHomeScreenLiteWithoutProtectedCustomers(isDesignSystemV2Enabled: Boolean = false) {
  LiteMoneyHomeScreen(
    model =
      LiteMoneyHomeBodyModel(
        onSettings = {},
        buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
        protectedCustomers = immutableListOf(),
        onProtectedCustomerClick = {},
        onBuyOwnBitkeyClick = {},
        onAcceptInviteClick = {},
        onIHaveABitkeyClick = {},
        isDesignSystemV2Enabled = isDesignSystemV2Enabled
      )
  )
}

@Preview
@Composable
fun MoneyHomeScreenFullNewWalletGettingStartedNoActivityPreview() {
  PreviewWalletTheme {
    MoneyHomeScreenFullNewWalletGettingStartedNoActivity()
  }
}

@Composable
fun MoneyHomeScreenFullNewWalletGettingStartedNoActivity() {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  MoneyHomeScreen(
    model =
      MoneyHomeBodyModel(
        onSettings = {},
        hideBalance = false,
        onHideBalance = {},
        balanceModel = MoneyAmountModel(
          primaryAmount = "$0.00",
          secondaryAmount = "0 sats",
          isLoading = false
        ),
        cardsModel = CardListModel(
          cards =
            immutableListOf(
              CardModel(
                title = null,
                content = CardModel.CardContent.BitcoinPrice(
                  isLoading = false,
                  price = "$90,000.00",
                  priceChange = "10.00% today",
                  priceDirection = PriceDirection.UP,
                  lastUpdated = "Updated 12:00am",
                  data =
                    immutableListOf(
                      DataPoint(1L, 90_000.0),
                      DataPoint(2L, 90_500.0),
                      DataPoint(3L, 90_250.0),
                      DataPoint(4L, 90_750.0),
                      DataPoint(5L, 90_600.0),
                      DataPoint(6L, 91_000.0),
                      DataPoint(7L, 90_900.0),
                      DataPoint(8L, 91_250.0),
                      DataPoint(9L, 91_100.0),
                      DataPoint(10L, 91_400.0)
                    )
                ),
                style = CardModel.CardStyle.Outline()
              ),
              GettingStartedCardModel(
                animations = null,
                taskModels =
                  immutableListOf(
                    GettingStartedTaskRowModel(
                      task = GettingStartedTask(AddBitcoin, Incomplete),
                      isEnabled = true,
                      onClick = {}
                    ),
                    GettingStartedTaskRowModel(
                      task = GettingStartedTask(EnableSpendingLimit, Incomplete),
                      isEnabled = true,
                      onClick = {}
                    )
                  )
              )
            )
        ),
        transactionsModel = null,
        seeAllButtonModel = null,
        coachmark = null,
        buttonsModel =
          MoneyHomeButtonsModel.MoneyMovementButtonsModel(
            addButton =
              MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                enabled = true,
                onClick = {}
              ),
            sellButton =
              MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                enabled = true,
                onClick = {}
              ),
            sendButton =
              MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                enabled = true,
                onClick = {}
              ),
            receiveButton =
              MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                enabled = true,
                onClick = {}
              )
          ),
        onRefresh = {},
        isRefreshing = false,
        onOpenPriceDetails = {},
        trailingToolbarAccessoryModel =
          ToolbarAccessoryModel.IconAccessory(
            model =
              IconButtonModel(
                iconModel = if (isDesignSystemV2Enabled) {
                  IconModel(
                    icon = MarketIcons.EllipsisHorizontal,
                    iconSize = IconSize.HeaderToolbar,
                    iconTint = IconTint.Foreground
                  )
                } else {
                  IconModel(
                    icon = Icon.SmallIconSettings,
                    iconSize = IconSize.HeaderToolbar
                  )
                },
                onClick = StandardClick {}
              )
          ),
        onSecurityHubTabClick = {},
        isSecurityHubBadged = false
      )
  )
}
