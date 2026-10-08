package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkWalletFactoryMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import uniffi.bdk.KeychainKind

class BdkWalletProviderImplV2Tests : FunSpec({
  lateinit var provider: BdkWalletProviderImpl
  lateinit var directory: File

  beforeTest {
    directory = Files.createTempDirectory("test-bdk2").toFile()
    provider = BdkWalletProviderImpl(
      bdkWalletFactory = BdkWalletFactoryMock(),
      bdkDatabaseConfigProvider = BdkDatabaseConfigProviderMock(),
      fileDirectoryProvider = object : FileDirectoryProvider {
        override fun appDir(): String = directory.absolutePath
      }
    )
  }

  afterTest {
    directory.deleteRecursively()
  }

  test("getPersister caches instances per identifier") {
    val persister1 = provider.getPersister("test-wallet")
    val persister2 = provider.getPersister("test-wallet")

    persister1.shouldBeSameInstanceAs(persister2)
  }

  test("getBdkWalletV2 caches wallet instances per identifier") {
    val descriptor =
      SpendingWalletDescriptor(
        identifier = "test-wallet",
        networkType = BitcoinNetworkType.BITCOIN,
        receivingDescriptor = Spending(EXTERNAL_DESCRIPTOR),
        changeDescriptor = Spending(INTERNAL_DESCRIPTOR)
      )

    val wallet1 = provider.getBdkWalletV2(descriptor).shouldBeOk()
    val wallet2 = provider.getBdkWalletV2(descriptor).shouldBeOk()

    wallet1.shouldBeSameInstanceAs(wallet2)
  }

  test("concurrent callers share one persister") {
    val persisters = concurrently { provider.getPersister("concurrent-persister") }
    persisters.forEach { it.shouldBeSameInstanceAs(persisters.first()) }
  }

  test("concurrent callers share one wallet") {
    val descriptor = SpendingWalletDescriptor(
      identifier = "concurrent-wallet",
      networkType = BitcoinNetworkType.BITCOIN,
      receivingDescriptor = Spending(EXTERNAL_DESCRIPTOR),
      changeDescriptor = Spending(INTERNAL_DESCRIPTOR)
    )
    val wallets = concurrently { provider.getBdkWalletV2(descriptor).shouldBeOk() }
    wallets.forEach { it.shouldBeSameInstanceAs(wallets.first()) }
    val address = wallets.first().revealNextAddress(KeychainKind.EXTERNAL)
    wallets.forEach { it.derivationIndex(KeychainKind.EXTERNAL).shouldBe(address.index) }
  }
})

private suspend fun <T> concurrently(block: () -> T): List<T> = coroutineScope {
  val ready = Channel<Unit>(8)
  val start = CompletableDeferred<Unit>()
  val callers = List(8) {
    async(Dispatchers.IO) {
      ready.send(Unit)
      start.await()
      block()
    }
  }
  repeat(callers.size) { ready.receive() }
  start.complete(Unit)
  callers.awaitAll()
}

private const val EXTERNAL_DESCRIPTOR =
  "wsh(sortedmulti(2,[a0a76f7a/84'/0'/0']xpub6CeEJqRzUbpUHsEkbz65NLzHYzrT5TpngE2pmuikVFJgSwU7hjEVpjeroQkdbGKLksMDnXPRxvif9jM2jBGxBXKdZYXNBLwG23tEqkAneEn/0/*,[18240e16/84'/0'/0']xpub6CF6yNxSPoNFJESR4xoxzTGKF6GZad5zdB14waPanqnkFWsNAuYNzq2y7nZk5TGiPcGjY8P3ZwrdixbsSp9uVMZ4QtRkMdUNHUsabMnPbKD/0/*,[afc9474a/84'/0'/0']xpub6CHktMjw1eKdvXgiYdzHVr58KfKnoZpZHqDmTD8MMPJA5vom1sd1TdrNnNESvrxXgZDUMeyBjg2wQYnmMXB767nwh5Z8eNDeFmdPrDdmKaq/0/*))"

private const val INTERNAL_DESCRIPTOR =
  "wsh(sortedmulti(2,[a0a76f7a/84'/0'/0']xpub6CeEJqRzUbpUHsEkbz65NLzHYzrT5TpngE2pmuikVFJgSwU7hjEVpjeroQkdbGKLksMDnXPRxvif9jM2jBGxBXKdZYXNBLwG23tEqkAneEn/1/*,[18240e16/84'/0'/0']xpub6CF6yNxSPoNFJESR4xoxzTGKF6GZad5zdB14waPanqnkFWsNAuYNzq2y7nZk5TGiPcGjY8P3ZwrdixbsSp9uVMZ4QtRkMdUNHUsabMnPbKD/1/*,[afc9474a/84'/0'/0']xpub6CHktMjw1eKdvXgiYdzHVr58KfKnoZpZHqDmTD8MMPJA5vom1sd1TdrNnNESvrxXgZDUMeyBjg2wQYnmMXB767nwh5Z8eNDeFmdPrDdmKaq/1/*))"
