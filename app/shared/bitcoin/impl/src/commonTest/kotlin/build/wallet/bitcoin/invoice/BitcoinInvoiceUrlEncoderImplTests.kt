package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressParserMock
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError.InvalidUri
import build.wallet.bitcoin.lightning.LightningInvoice
import build.wallet.bitcoin.lightning.LightningInvoiceParserMock
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErr
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinInvoiceUrlEncoderImplTests : FunSpec({
  val testAddressString = "15e15hWo6CShMgbAfo8c2Ykj4C6BLq6Not"
  val testInvalidAddressString = testAddressString + "1"
  val testAddress = BitcoinAddress(testAddressString)

  val testLightningInvoiceString =
    "LNBC10U1P3PJ257PP5YZTKWJCZ5FTL5LAXKAV23ZMZEKAW37ZK6KMV80PK4XA" +
      "EV5QHTZ7QDPDWD3XGER9WD5KWM36YPRX7U3QD36KUCMGYP282ETNV3SHJCQZPGXQYZ5VQSP5USYC4LK9CHSFP53KVCNV" +
      "Q456GANH60D89REYKDNGSMTJ6YW3NHVQ9QYYSSQJCEWM5CJWZ4A6RFJX77C490YCED6PEMK0UPKXHY89CMM7SCT66K8G" +
      "NEANWYKZGDRWRFJE69H9U5U0W57RRCSYSAS7GADWMZXC8C6T0SPJAZUP6"
  val testInvalidLightningInvoiceString = testAddressString + "1"

  val addressParser = BitcoinAddressParserMock()
  addressParser.parseResult = Ok(testAddress)
  val lightningInvoiceParser =
    LightningInvoiceParserMock(
      validInvoices = mutableSetOf(testLightningInvoiceString)
    )
  val invoiceUrlEncoder = BitcoinInvoiceUrlEncoderImpl(addressParser, lightningInvoiceParser)

  fun BitcoinInvoice.encode() = invoiceUrlEncoder.encode(this)

  fun String.decode() = invoiceUrlEncoder.decode(this, BITCOIN)

  test("encode address") {
    val invoice = BitcoinInvoice(address = testAddress)
    invoice.encode().shouldBe("bitcoin:$testAddressString")
  }

  test("encode address with parameters") {
    val invoice =
      BitcoinInvoice(
        address = testAddress,
        amount = BitcoinMoney.btc(1.23),
        label = "To: Satoshi",
        message = "for pizza",
        customParameters = mapOf("customParam" to "from: me")
      )
    invoice.encode()
      .shouldBe(
        "bitcoin:$testAddressString?amount=1.23&label=To%3A+Satoshi&message=for+pizza&customParam=from%3A+me"
      )
  }

  test("decode with invalid address") {
    testInvalidAddressString.decode()
      .shouldBeErr(InvalidUri)
  }

  test("decode with scheme") {
    "bitcoin:$testAddressString"
      .decode()
      .shouldBe(
        Ok(
          BIP21PaymentData(
            onchainInvoice = BitcoinInvoice(address = testAddress),
            lightningInvoice = null
          )
        )
      )
  }

  test("decode with uppercase scheme") {
    "BITCOIN:$testAddressString"
      .decode()
      .shouldBe(
        Ok(
          BIP21PaymentData(
            onchainInvoice = BitcoinInvoice(address = testAddress),
            lightningInvoice = null
          )
        )
      )
  }

  test("decode with mixed case scheme") {
    "BiTcOiN:$testAddressString"
      .decode()
      .shouldBe(
        Ok(
          BIP21PaymentData(
            onchainInvoice = BitcoinInvoice(address = testAddress),
            lightningInvoice = null
          )
        )
      )
  }

  test("decode with invalid scheme") {
    "btc:$testAddressString"
      .decode()
      .shouldBe(
        Err(BitcoinInvoiceUrlEncoderError.InvalidUri)
      )
  }

  test("decode with parameters percent '%20' encoding") {
    "bitcoin:$testAddressString?amount=1.23&label=To:%20Satoshi&message=for%20pizza&customParam=from:%20me"
      .decode()
      .shouldBe(
        Ok(
          BIP21PaymentData(
            onchainInvoice =
              BitcoinInvoice(
                address = testAddress,
                amount = BitcoinMoney.btc(1.23),
                label = "To: Satoshi",
                message = "for pizza",
                customParameters = mapOf("customParam" to "from: me")
              ),
            lightningInvoice = null
          )
        )
      )
  }

  test("decode with parameters plus '+' encoding") {
    "bitcoin:$testAddressString?amount=1.23&label=To:+Satoshi&message=for+pizza&customParam=from:+me"
      .decode()
      .shouldBe(
        Ok(
          BIP21PaymentData(
            onchainInvoice =
              BitcoinInvoice(
                address = testAddress,
                amount = BitcoinMoney.btc(1.23),
                label = "To: Satoshi",
                message = "for pizza",
                customParameters = mapOf("customParam" to "from: me")
              ),
            lightningInvoice = null
          )
        )
      )
  }

  test("decode with parameters plus '+' encoding and character encoding") {
    "bitcoin:$testAddressString?amount=1.23&label=To%3A+Satoshi&message=for+pizza&customParam=from%3A+me"
      .decode()
      .shouldBe(
        Ok(
          BIP21PaymentData(
            onchainInvoice =
              BitcoinInvoice(
                address = testAddress,
                amount = BitcoinMoney.btc(1.23),
                label = "To: Satoshi",
                message = "for pizza",
                customParameters = mapOf("customParam" to "from: me")
              ),
            lightningInvoice = null
          )
        )
      )
  }

  test("decode with invalid amount") {
    "bitcoin:$testAddressString?amount=invalid"
      .decode()
      .shouldBe(
        Ok(
          BIP21PaymentData(
            onchainInvoice = BitcoinInvoice(address = testAddress),
            lightningInvoice = null
          )
        )
      )
  }

  test("decode with no address") {
    "bitcoin:?amount=1.23"
      .decode()
      .shouldBe(
        Err(BitcoinInvoiceUrlEncoderError.InvalidUri)
      )
  }

  test("decode with required unknown field") {
    "bitcoin:$testAddressString?req-param=important"
      .decode()
      .shouldBe(
        Err(BitcoinInvoiceUrlEncoderError.InvalidUri)
      )
  }

  test("decode with two amount parameters") {
    "bitcoin:$testAddressString?amount=0.01&amount=5.6"
      .decode()
      .shouldBe(
        Err(InvalidUri)
      )
  }

  test("decode with valid lightning invoice") {
    val paymentData =
      "bitcoin:$testAddressString?amount=0.00001&lightning=$testLightningInvoiceString"
        .decode()

    paymentData.shouldBe(
      Ok(
        BIP21PaymentData(
          onchainInvoice =
            BitcoinInvoice(
              address = testAddress,
              amount = BitcoinMoney.btc(0.00001),
              customParameters = mapOf("lightning" to testLightningInvoiceString)
            ),
          lightningInvoice =
            LightningInvoice(
              payeePubKey = "037cc5f9f1da20ac0d60e83989729a204a33cc2d8e80438969fadf35c1c5f1233b",
              paymentHash = "2097674b02a257fa7fa6b758a88b62cdbae8f856d5b6c3bc36a9bb96501758bc",
              isExpired = true,
              amountMsat = 1_000_000UL
            )
        )
      )
    )
  }

  test("decode with invalid lightning invoice") {
    val paymentData =
      "bitcoin:$testAddressString?amount=0.00001&lightning=$testInvalidLightningInvoiceString"
        .decode()

    paymentData.shouldBe(
      Ok(
        BIP21PaymentData(
          onchainInvoice =
            BitcoinInvoice(
              address = testAddress,
              amount = BitcoinMoney.btc(0.00001),
              customParameters = mapOf("lightning" to testInvalidLightningInvoiceString)
            ),
          lightningInvoice = null
        )
      )
    )
  }
})
