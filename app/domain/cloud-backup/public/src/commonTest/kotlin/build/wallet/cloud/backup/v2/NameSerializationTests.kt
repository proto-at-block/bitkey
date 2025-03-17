package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.BitcoinNetworkType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual

class NameSerializationTests : FunSpec({

  test("BitcoinNetworkType names don't change!") {
    val names = BitcoinNetworkType.entries.map { it.name }
    names.shouldBeEqual(
      listOf(
        "BITCOIN",
        "SIGNET",
        "TESTNET",
        "REGTEST"
      )
    )
  }
})
