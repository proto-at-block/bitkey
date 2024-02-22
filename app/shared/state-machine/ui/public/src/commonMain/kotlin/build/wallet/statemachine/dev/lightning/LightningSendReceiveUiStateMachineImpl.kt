package build.wallet.statemachine.dev.lightning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.lightning.LightningInvoiceParser
import build.wallet.ldk.LdkNodeService
import build.wallet.ldk.bindings.Invoice
import build.wallet.logging.log
import build.wallet.money.BitcoinMoney
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.BodyModel
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

class LightningSendReceiveUiStateMachineImpl(
  private val ldkNodeService: LdkNodeService,
  private val lightningInvoiceParser: LightningInvoiceParser,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : LightningSendReceiveUiStateMachine {
  @Composable
  override fun model(props: LightningSendReceiveUiProps): BodyModel {
    // Fields that generate some side effects
    var sendingPayment: Invoice? by remember { mutableStateOf(null) }
    var generatedInvoice: Invoice? by remember { mutableStateOf(null) }
    var invoiceToGenerate: InvoiceGenerationParams? by remember { mutableStateOf(null) }
    var amountToReceive: BigInteger by remember { mutableStateOf(BigInteger.ZERO) }
    var invoiceInputString: String by remember { mutableStateOf("") }

    sendingPayment?.let {
      SendLightningPaymentEffect(
        it,
        onSent = {
          generatedInvoice = null
        }
      )
    }

    invoiceToGenerate?.let {
      GenerateInvoiceEffect(
        it,
        onInvoiceGenerated = { invoice ->
          generatedInvoice = invoice
          invoiceToGenerate = null
        }
      )
    }

    return LightningSendReceiveBodyModel(
      amountToReceive = amountToReceive.stringRepresentation.orEmpty(),
      invoiceInputValue = invoiceInputString,
      lightningBalance = rememberFormattedLightningBalance(),
      generatedInvoiceString = generatedInvoice,
      onAmountToReceiveChanged = { amountInputString ->
        amountToReceive = amountInputString.toBigInteger()
      },
      onLightningInvoiceChanged = {
        invoiceInputString = it
      },
      handleSendButtonPressed = {
        lightningInvoiceParser.parse(invoiceInputString)?.let {
          sendingPayment = invoiceInputString
        }
      },
      handleGenerateInvoicePressed = {
        invoiceToGenerate =
          InvoiceGenerationParams(
            amountToReceive = amountToReceive,
            description = ""
          )
      },
      onBack = props.onBack
    )
  }

  @Composable
  private fun rememberFormattedLightningBalance(): String {
    return produceState("...") {
      val balance = ldkNodeService.totalLightningBalance()
      value = moneyDisplayFormatter.format(BitcoinMoney.sats(balance))
    }.value
  }

  @Composable
  private fun SendLightningPaymentEffect(
    invoice: Invoice,
    onSent: () -> Unit,
  ) {
    LaunchedEffect("send-payment", invoice) {
      ldkNodeService.sendPayment(invoice)
      onSent()
    }
  }

  @Composable
  private fun GenerateInvoiceEffect(
    params: InvoiceGenerationParams,
    onInvoiceGenerated: (Invoice) -> Unit,
  ) {
    ldkNodeService.receivePayment(
      amountMsat = params.amountToReceive,
      description = params.description
    ).onSuccess { invoice ->
      log { "Generated Invoice: $invoice" }
      onInvoiceGenerated(invoice)
    }
  }
}

private data class InvoiceGenerationParams(
  val amountToReceive: BigInteger,
  val description: String,
)
