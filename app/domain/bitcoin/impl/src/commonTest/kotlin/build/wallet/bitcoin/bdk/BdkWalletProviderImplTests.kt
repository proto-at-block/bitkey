package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkWalletFactoryMock
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import com.github.michaelbull.result.get
import io.kotest.core.config.ProjectConfiguration.Companion.MaxConcurrency
import io.kotest.core.spec.IsolationMode.SingleInstance
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.async

class BdkWalletProviderImplTests : FunSpec({
  /**
   * Tweak spec to to run tests concurrently to simulate potential thread race conditions
   * as possibly as we can.
   */
  isolationMode = SingleInstance
  concurrency = MaxConcurrency

  val walletDescriptor =
    SpendingWalletDescriptor(
      identifier = "wallet",
      networkType = BITCOIN,
      receivingDescriptor = Spending("receiving"),
      changeDescriptor = Spending("receiving")
    )

  val provider =
    BdkWalletProviderImpl(
      bdkWalletFactory = BdkWalletFactoryMock(),
      bdkDatabaseConfigProvider = BdkDatabaseConfigProviderMock()
    )

  test("wallet instance is cached, thread safe").config(invocations = 10) {
    val wallet1Deferred =
      async(start = UNDISPATCHED) {
        provider.getBdkWallet(walletDescriptor)
      }
    val wallet2Deferred =
      async(start = UNDISPATCHED) {
        provider.getBdkWallet(walletDescriptor)
      }
    val wallet1 = wallet1Deferred.await().get().shouldNotBeNull()
    val wallet2 = wallet2Deferred.await().get().shouldNotBeNull()
    wallet1.shouldBeSameInstanceAs(wallet2)
  }
})
