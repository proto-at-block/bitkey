package build.wallet.bitcoin.address

import build.wallet.bdk.bindings.BdkAddressBuilderMock
import build.wallet.bdk.bindings.BdkAddressMock
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError.InvalidNetwork
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError.InvalidScript
import build.wallet.coroutines.turbine.turbines
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class BitcoinAddressParserImplTests : FunSpec({
  val addressBuilder = BdkAddressBuilderMock(turbines::create)
  val parser = BitcoinAddressParserImpl(addressBuilder)

  test("address builder returns error") {
    addressBuilder.buildFromAddressReturn = BdkResult.Err(BdkError.Generic(null, null))
    parser.parse("abc", BITCOIN).shouldBeErrOfType<InvalidScript>()
  }

  test("address matches network") {
    addressBuilder.buildFromAddressReturn = BdkResult.Ok(BdkAddressMock())
    parser.parse("abc", BITCOIN).shouldBeOk()
  }

  test("address does not match network") {
    addressBuilder.buildFromAddressReturn = BdkResult.Ok(BdkAddressMock())
    parser.parse("abc", SIGNET).shouldBeErrOfType<InvalidNetwork>()
  }
})
