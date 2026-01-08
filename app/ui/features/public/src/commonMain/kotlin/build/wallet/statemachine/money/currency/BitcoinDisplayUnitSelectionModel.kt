package build.wallet.statemachine.money.currency

import bitkey.ui.Snapshot
import bitkey.ui.SnapshotHost
import build.wallet.analytics.events.screen.id.AppearanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.displayText
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun bitcoinDisplayUnitSelectionSheetModel(
  selectedUnit: BitcoinDisplayUnit,
  balance: BitcoinMoney,
  isBip177Enabled: Boolean,
  moneyDisplayFormatter: MoneyDisplayFormatter,
  onSelectUnit: (BitcoinDisplayUnit) -> Unit,
  onExit: () -> Unit,
): SheetModel {
  val items = BitcoinDisplayUnit.entries.map { unit ->
    ListItemModel(
      title = unit.displayText(isBip177Enabled),
      secondaryText = moneyDisplayFormatter.formatWithUnit(balance, unit),
      treatment = ListItemTreatment.PRIMARY,
      onClick = { onSelectUnit(unit) },
      selected = selectedUnit == unit,
      trailingAccessory = if (selectedUnit == unit) {
        ListItemAccessory.IconAccessory(
          model = IconModel(
            icon = Icon.SmallIconCheckFilled,
            iconSize = IconSize.Small,
            iconTint = IconTint.Primary
          )
        )
      } else {
        null
      }
    )
  }.toImmutableList()

  return SheetModel(
    body = BitcoinDisplayUnitSelectionBodyModel(
      items = items,
      selectedUnit = selectedUnit,
      isBip177Enabled = isBip177Enabled,
      onSelectUnit = onSelectUnit
    ),
    onClosed = onExit,
    size = SheetSize.DEFAULT
  )
}

data class BitcoinDisplayUnitSelectionBodyModel(
  val items: ImmutableList<ListItemModel>,
  val selectedUnit: BitcoinDisplayUnit,
  val isBip177Enabled: Boolean,
  val onSelectUnit: (BitcoinDisplayUnit) -> Unit,
) : FormBodyModel(
    id = AppearanceEventTrackerScreenId.BITCOIN_DISPLAY_UNIT_SELECTION,
    header = FormHeaderModel(
      headline = "Bitcoin display unit",
      subline = if (isBip177Enabled) "1 BTC = ₿100,000,000" else "1 BTC = 100,000,000 sats",
      sublineTreatment = SublineTreatment.SMALL
    ),
    onBack = {},
    toolbar = null,
    mainContentList =
      immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel =
            ListGroupModel(
              style = ListGroupStyle.DIVIDER,
              items = items
            )
        )
      ),
    primaryButton = null,
    renderContext = RenderContext.Sheet
  )

@Snapshot
val SnapshotHost.bitcoinDisplayUnitSelectionBitcoinSelected
  get() = bitcoinDisplayUnitSnapshotModel(
    selectedUnit = BitcoinDisplayUnit.Bitcoin,
    isBip177Enabled = true
  )

@Snapshot
val SnapshotHost.bitcoinDisplayUnitSelectionSatoshiSelected
  get() = bitcoinDisplayUnitSnapshotModel(
    selectedUnit = BitcoinDisplayUnit.Satoshi,
    isBip177Enabled = true
  )

@Snapshot
val SnapshotHost.bitcoinDisplayUnitSelectionSatoshiSelectedLegacy
  get() = bitcoinDisplayUnitSnapshotModel(
    selectedUnit = BitcoinDisplayUnit.Satoshi,
    isBip177Enabled = false
  )

private fun bitcoinDisplayUnitSnapshotModel(
  selectedUnit: BitcoinDisplayUnit,
  isBip177Enabled: Boolean,
) = BitcoinDisplayUnitSelectionBodyModel(
  items = BitcoinDisplayUnit.entries.map { unit ->
    ListItemModel(
      title = unit.displayText(isBip177Enabled = isBip177Enabled),
      secondaryText = when (unit) {
        BitcoinDisplayUnit.Bitcoin -> "0.001 BTC"
        BitcoinDisplayUnit.Satoshi -> if (isBip177Enabled) "₿100,000" else "100,000 sats"
      },
      treatment = ListItemTreatment.PRIMARY,
      onClick = {},
      selected = unit == selectedUnit,
      trailingAccessory = if (unit == selectedUnit) {
        ListItemAccessory.IconAccessory(
          model = IconModel(
            icon = Icon.SmallIconCheckFilled,
            iconSize = IconSize.Small,
            iconTint = IconTint.Primary
          )
        )
      } else {
        null
      }
    )
  }.toImmutableList(),
  selectedUnit = selectedUnit,
  isBip177Enabled = isBip177Enabled,
  onSelectUnit = {}
)
