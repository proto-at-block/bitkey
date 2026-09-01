package build.wallet.money.formatter

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.BitcoinDisplayUnit

@BitkeyInject(AppScope::class)
class MoneyDisplayFormatterImpl(
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val moneyFormatterDefinitions: MoneyFormatterDefinitions,
) : MoneyDisplayFormatter {
  override fun format(amount: Money) =
    when (amount) {
      is FiatMoney -> format(amount)
      is BitcoinMoney -> format(amount)
    }

  override fun formatCompact(amount: FiatMoney) =
    moneyFormatterDefinitions.fiatCompact.stringValue(amount)

  private fun format(amount: FiatMoney) = moneyFormatterDefinitions.fiatStandard.stringValue(amount)

  override fun formatWithUnit(
    amount: BitcoinMoney,
    unit: BitcoinDisplayUnit,
  ): String = formatterForUnit(unit).stringValue(amount)

  private fun format(amount: BitcoinMoney): String {
    val displayUnit = bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value
    return formatterForUnit(displayUnit).stringValue(amount)
  }

  private fun formatterForUnit(unit: BitcoinDisplayUnit): BitcoinMoneyFormatter =
    when (unit) {
      BitcoinDisplayUnit.Bitcoin -> moneyFormatterDefinitions.bitcoinReducedCode
      // BIP 177: Use ₿ symbol prefix instead of "sats" suffix
      BitcoinDisplayUnit.Satoshi -> moneyFormatterDefinitions.bitcoinFractionalBip177
    }
}
