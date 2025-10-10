package build.wallet.statemachine.recovery.orphaned

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.ORPHANED_ACCOUNT_SELECTION
import build.wallet.bitkey.f8e.extractUlidTimestampFromUrn
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.recovery.OrphanedKeyRecoveryService.RecoverableAccount
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

/**
 * UI model for selecting which orphaned account to recover when multiple accounts are found.
 */
fun OrphanedAccountSelectionBodyModel(
  accounts: List<RecoverableAccount>,
  selectedAccount: RecoverableAccount?,
  onAccountSelected: (RecoverableAccount) -> Unit,
  onRecover: () -> Unit,
  onBack: () -> Unit,
  moneyDisplayFormatter: MoneyDisplayFormatter,
  dateTimeFormatter: DateTimeFormatter,
  timeZoneProvider: TimeZoneProvider,
): FormBodyModel =
  object : FormBodyModel(
    id = ORPHANED_ACCOUNT_SELECTION,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Recoverable Accounts",
      subline = if (accounts.size == 1) {
        "We found 1 account that can be recovered."
      } else {
        "We found ${accounts.size} accounts. Select which account to recover."
      }
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = accounts
            // Cache timestamp for each account to avoid repeated parsing
            .map { account ->
              account to extractUlidTimestampFromUrn(account.accountId.serverId)
            }
            .sortedByDescending { (_, timestamp) -> timestamp }
            .mapIndexed { index, (account, createdTimestamp) ->
              val balanceText = account.balance?.total?.let {
                moneyDisplayFormatter.format(it)
              } ?: "Balance unavailable"
              val isSelected = selectedAccount?.accountId == account.accountId

              val createdDate = Instant.fromEpochMilliseconds(createdTimestamp)
                .toLocalDateTime(timeZoneProvider.current())
              val formattedDate = dateTimeFormatter.shortDateWithYear(createdDate)

              ListItemModel(
                leadingAccessory = ListItemAccessory.IconAccessory(
                  iconPadding = 12,
                  model = IconModel(
                    icon = Icon.SmallIconWallet,
                    iconSize = IconSize.Small
                  )
                ),
                title = "Account ${index + 1}",
                secondaryText = "$balanceText\nCreated $formattedDate",
                onClick = if (accounts.size > 1) {
                  { onAccountSelected(account) }
                } else {
                  null
                },
                trailingAccessory = if (accounts.size > 1 && isSelected) {
                  ListItemAccessory.IconAccessory(
                    model = IconModel(
                      icon = Icon.SmallIconCheckFilled,
                      iconSize = IconSize.XSmall,
                      iconTint = IconTint.Primary
                    )
                  )
                } else {
                  null
                }
              )
            }
            .toImmutableList(),
          style = ListGroupStyle.CARD_ITEM
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Recover Wallet",
      isEnabled = selectedAccount != null,
      onClick = StandardClick(onRecover),
      size = Footer
    )
  ) {}
