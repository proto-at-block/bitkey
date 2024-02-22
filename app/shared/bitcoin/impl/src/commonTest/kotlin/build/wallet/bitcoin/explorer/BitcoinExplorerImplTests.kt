package build.wallet.bitcoin.explorer

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.explorer.BitcoinExplorerType.Mempool
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinExplorerImplTests : FunSpec({
  val explorer = BitcoinExplorerImpl()
  val txId = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"

  test("bitcoin network") {
    explorer.getTransactionUrl(
      txId = txId,
      network = BITCOIN,
      explorerType = Mempool
    ).shouldBe("https://mempool.space/tx/$txId")
  }

  test("signet network") {
    explorer.getTransactionUrl(
      txId = txId,
      network = SIGNET,
      explorerType = Mempool
    ).shouldBe("https://mempool.space/signet/tx/$txId")
  }

  test("testnet network") {
    explorer.getTransactionUrl(
      txId = txId,
      network = TESTNET,
      explorerType = Mempool
    ).shouldBe("https://mempool.space/testnet/tx/$txId")
  }

  test("regtest network") {
    explorer.getTransactionUrl(
      txId = txId,
      network = REGTEST,
      explorerType = Mempool
    ).shouldBe("https://mempool.space/regtest/tx/$txId")
  }
})
