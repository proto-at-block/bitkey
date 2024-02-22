package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.address.BitcoinAddressParser
import build.wallet.bitcoin.address.BitcoinAddressParserMock
import build.wallet.bitcoin.invoice.ParsedPaymentData.BIP21
import build.wallet.bitcoin.invoice.ParsedPaymentData.Lightning
import build.wallet.bitcoin.invoice.ParsedPaymentData.Onchain
import build.wallet.bitcoin.lightning.LightningInvoiceParserMock
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf

class PaymentDataParserImplTests : FunSpec({
  val testAddressString = "BC1QYLH3U67J673H6Y6ALV70M0PL2YZ53TZHVXGG7U"
  val testLightningInvoiceString =
    "LNBC10U1P3PJ257PP5YZTKWJCZ5FTL5LAXKAV23ZMZEKAW37ZK6KMV80PK4XA" +
      "EV5QHTZ7QDPDWD3XGER9WD5KWM36YPRX7U3QD36KUCMGYP282ETNV3SHJCQZPGXQYZ5VQSP5USYC4LK9CHSFP53KVCNV" +
      "Q456GANH60D89REYKDNGSMTJ6YW3NHVQ9QYYSSQJCEWM5CJWZ4A6RFJX77C490YCED6PEMK0UPKXHY89CMM7SCT66K8G" +
      "NEANWYKZGDRWRFJE69H9U5U0W57RRCSYSAS7GADWMZXC8C6T0SPJAZUP6"
  val testBIP21URI =
    "bitcoin:$testAddressString?amount=1.23&label=To%3A+Satoshi&message=for+pizz" +
      "a&customParam=from%3A+me"

  val invalidAddressString = "BC1QYLH3U67J673H6Y6ALV70M0PL2YZ53TZHVXGG7"
  val invalidBIP21URI =
    "bitcoin:$invalidAddressString?amount=1.23&label=To%3A+Satoshi&message=for+pizz" +
      "a&customParam=from%3A+me"

  val addressParser = BitcoinAddressParserMock()
  val lightningInvoiceParser =
    LightningInvoiceParserMock(
      validInvoices = mutableSetOf(testLightningInvoiceString)
    )
  val invoiceUrlEncoder = BitcoinInvoiceUrlEncoderImpl(addressParser, lightningInvoiceParser)

  val paymentDataParser =
    PaymentDataParserImpl(
      bip21InvoiceEncoder = invoiceUrlEncoder,
      bitcoinAddressParser = addressParser,
      lightningInvoiceParser = lightningInvoiceParser
    )

  beforeTest {
    addressParser.reset()
  }

  test("Parse onchain-only address") {
    paymentDataParser.decode(testAddressString, BITCOIN)
      .shouldBeTypeOf<Ok<Onchain>>()
  }

  test("Parse BIP21 URI") {
    paymentDataParser.decode(testBIP21URI, BITCOIN)
      .shouldBeTypeOf<Ok<BIP21>>()
  }

  test("Parse Lightning invoice") {
    paymentDataParser.decode(testLightningInvoiceString, BITCOIN)
      .shouldBeTypeOf<Ok<Lightning>>()
  }

  test("Invalid onchain-only address – invalid network") {
    addressParser.parseResult = Err(BitcoinAddressParser.BitcoinAddressParserError.InvalidNetwork)
    paymentDataParser.decode(testAddressString, SIGNET)
      .shouldBeErrOfType<PaymentDataParser.PaymentDataParserError.InvalidNetwork>()
  }

  test("Invalid onchain-only address – invalid address") {
    addressParser.parseResult = Err(BitcoinAddressParser.BitcoinAddressParserError.InvalidScript(Throwable()))
    paymentDataParser.decode(invalidAddressString, BITCOIN)
      .shouldBeErrOfType<PaymentDataParser.PaymentDataParserError.InvalidBIP21URI>()
  }

  test("Invalid BIP21 URI – invalid network") {
    addressParser.parseResult = Err(BitcoinAddressParser.BitcoinAddressParserError.InvalidNetwork)
    paymentDataParser.decode(testBIP21URI, SIGNET)
      .shouldBeErrOfType<PaymentDataParser.PaymentDataParserError.InvalidNetwork>()
  }

  test("Invalid BIP21 URI – invalid address") {
    addressParser.parseResult = Err(BitcoinAddressParser.BitcoinAddressParserError.InvalidScript(Throwable()))
    paymentDataParser.decode(invalidBIP21URI, BITCOIN)
      .shouldBeErrOfType<PaymentDataParser.PaymentDataParserError.InvalidBIP21URI>()
  }
})
