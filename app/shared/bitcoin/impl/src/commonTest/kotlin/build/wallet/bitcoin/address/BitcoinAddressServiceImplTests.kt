package build.wallet.bitcoin.address

import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.transactions.TransactionsServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.F8eSpendingPublicKeyMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.queueprocessor.ProcessorMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class BitcoinAddressServiceImplTests : FunSpec({

  coroutineTestScope = true

  val spendingWallet = SpendingWalletMock(turbines::create)
  val registerWatchAddressProcessor =
    ProcessorMock<RegisterWatchAddressContext>(turbines::create)
  val transactionService = TransactionsServiceFake()
  val service = BitcoinAddressServiceImpl(
    registerWatchAddressProcessor = registerWatchAddressProcessor,
    transactionsService = transactionService
  )

  beforeTest {
    spendingWallet.reset()
    registerWatchAddressProcessor.reset()
    transactionService.reset()
    transactionService.spendingWallet.value = spendingWallet
  }

  test("generate new address successfully") {
    backgroundScope.launch {
      service.executeWork()
    }

    spendingWallet.newAddressResult = Ok(someBitcoinAddress)
    registerWatchAddressProcessor.processBatchReturnValues = listOf(Ok(Unit))

    val addressResult = service.generateAddress(FullAccountMock)
    addressResult.shouldBe(Ok(someBitcoinAddress))

    registerWatchAddressProcessor.processBatchCalls.awaitItem().shouldBe(
      listOf(
        RegisterWatchAddressContext(
          someBitcoinAddress,
          F8eSpendingKeyset(
            keysetId = "spending-public-keyset-fake-server-id",
            spendingPublicKey = F8eSpendingPublicKeyMock
          ),
          FullAccountIdMock.serverId,
          KeyboxMock.config.f8eEnvironment
        )
      )
    )
  }

  test("generate new address - address generation failure") {
    backgroundScope.launch {
      service.executeWork()
    }

    val error = Err(Generic(Exception("failed to generate address"), null))
    spendingWallet.newAddressResult = error

    val addressResult = service.generateAddress(FullAccountMock)
    addressResult.shouldBe(error)

    registerWatchAddressProcessor.processBatchCalls.expectNoEvents()
  }
})
