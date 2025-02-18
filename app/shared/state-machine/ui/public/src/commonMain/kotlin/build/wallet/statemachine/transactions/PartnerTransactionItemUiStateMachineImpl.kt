package build.wallet.statemachine.transactions

import androidx.compose.runtime.*
import build.wallet.activity.Transaction
import build.wallet.activity.bitcoinTotal
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.PartnershipTransactionStatus
import build.wallet.partnerships.PartnershipTransactionType
import build.wallet.statemachine.data.money.convertedOrNull
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint.GREEN
import build.wallet.ui.model.list.ListItemSideTextTint.PRIMARY
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class PartnerTransactionItemUiStateMachineImpl(
  val currencyConverter: CurrencyConverter,
  val moneyDisplayFormatter: MoneyDisplayFormatter,
  val dateTimeFormatter: DateTimeFormatter,
  val timeZoneProvider: TimeZoneProvider,
  val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  val clock: Clock,
) : PartnerTransactionItemUiStateMachine {
  @Composable
  override fun model(props: PartnerTransactionItemUiProps): ListItemModel {
    val fiatCurrency by remember { fiatCurrencyPreferenceRepository.fiatCurrencyPreference }
      .collectAsState()

    val totalToUse = when (val bitcoinTransaction = props.transaction.bitcoinTransaction) {
      null -> props.transaction.details.cryptoAmount?.let { BitcoinMoney(value = it.toBigDecimal()) }
      else -> when (bitcoinTransaction.transactionType) {
        Incoming, UtxoConsolidation -> bitcoinTransaction.subtotal
        Outgoing -> bitcoinTransaction.total
      }
    }

    val fiatAmount = totalToUse?.let {
      convertedOrNull(
        converter = currencyConverter,
        fromAmount = it,
        toCurrency = fiatCurrency,
        atTime = props.transaction.bitcoinTransaction?.let { bitcoinTransaction ->
          when (bitcoinTransaction.transactionType) {
            Incoming, UtxoConsolidation -> bitcoinTransaction.confirmationTime()
            Outgoing -> bitcoinTransaction.broadcastTime ?: bitcoinTransaction.confirmationTime()
          }
        }
      )
    }

    val fiatAmountFormatted by remember(fiatAmount) {
      derivedStateOf {
        val prefix = when (props.transaction.details.type) {
          PartnershipTransactionType.PURCHASE, PartnershipTransactionType.TRANSFER -> "+ "
          PartnershipTransactionType.SALE -> ""
        }

        val formatted = fiatAmount?.let {
          "$prefix${moneyDisplayFormatter.format(it)}"
        }

        formatted ?: "~~"
      }
    }

    with(props.transaction) {
      return PartnerTransactionItemModel(
        title = when (details.type) {
          PartnershipTransactionType.PURCHASE -> "Purchase"
          PartnershipTransactionType.TRANSFER -> "Transfer"
          PartnershipTransactionType.SALE -> "Sale"
        },
        date = formattedDateTime(),
        logoUrl = when (details.status) {
          PartnershipTransactionStatus.PENDING, PartnershipTransactionStatus.FAILED ->
            details.partnerInfo.logoBadgedUrl
          else -> details.partnerInfo.logoUrl
        },
        amount = fiatAmountFormatted,
        amountEquivalent = bitcoinTotal()?.let { moneyDisplayFormatter.format(it) } ?: "",
        isPending = details.status != PartnershipTransactionStatus.SUCCESS,
        isError = details.status == PartnershipTransactionStatus.FAILED,
        onClick = { props.onClick(props.transaction) },
        sideTextTint = when (details.type) {
          PartnershipTransactionType.PURCHASE, PartnershipTransactionType.TRANSFER -> GREEN
          PartnershipTransactionType.SALE -> PRIMARY
        }
      )
    }
  }

  private fun Transaction.PartnershipTransaction.formattedDateTime(): String {
    return when (details.status) {
      PartnershipTransactionStatus.PENDING -> "Pending"
      PartnershipTransactionStatus.FAILED -> "Failed"
      else -> details.created.toLocalDateTime(timeZoneProvider.current())
        .let { dateTimeFormatter.shortDateWithTime(it) }
    }
  }
}
