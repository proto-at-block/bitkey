package build.wallet.bitcoin.address

import build.wallet.account.AccountServiceFake
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.turbines
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.notifications.RegisterWatchAddressProcessor
import build.wallet.queueprocessor.Processor
import build.wallet.queueprocessor.ProcessorMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class BitcoinAddressServiceImplTests : FunSpec({

  val spendingWallet = SpendingWalletMock(turbines::create)
  val processorMock = ProcessorMock<RegisterWatchAddressContext>(turbines::create)
  val registerWatchAddressProcessor = object :
    RegisterWatchAddressProcessor,
    Processor<RegisterWatchAddressContext> by processorMock {}
  val transactionService = BitcoinWalletServiceFake()
  val accountService = AccountServiceFake()
  val service = BitcoinAddressServiceImpl(
    registerWatchAddressProcessor = registerWatchAddressProcessor,
    bitcoinWalletService = transactionService,
    accountService = accountService
  )

  beforeTest {
    spendingWallet.reset()
    processorMock.reset()
    transactionService.reset()
    transactionService.spendingWallet.value = spendingWallet
    accountService.reset()
  }

  test("generate new address successfully") {
    accountService.setActiveAccount(FullAccountMock)

    createBackgroundScope().launch {
      service.executeWork()
    }

    spendingWallet.newAddressResult = Ok(someBitcoinAddress)
    processorMock.processBatchReturnValues = listOf(Ok(Unit))

    val addressResult = service.generateAddress()
    addressResult.shouldBe(Ok(someBitcoinAddress))

    processorMock.processBatchCalls.awaitItem().shouldBe(
      listOf(
        RegisterWatchAddressContext(
          someBitcoinAddress,
          F8eSpendingKeysetMock,
          FullAccountIdMock.serverId,
          KeyboxMock.config.f8eEnvironment
        )
      )
    )
  }

  test("generate new address - address generation failure") {
    accountService.setActiveAccount(FullAccountMock)

    val error = Err(Generic(Exception("failed to generate address"), null))
    spendingWallet.newAddressResult = error

    val addressResult = service.generateAddress()
    addressResult.shouldBe(error)

    processorMock.processBatchCalls.expectNoEvents()
  }
})
