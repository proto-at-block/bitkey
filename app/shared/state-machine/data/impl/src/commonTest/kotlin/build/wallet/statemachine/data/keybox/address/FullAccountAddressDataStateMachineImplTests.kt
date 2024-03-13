package build.wallet.statemachine.data.keybox.address

import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.F8eSpendingPublicKeyMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.queueprocessor.ProcessorMock
import build.wallet.statemachine.core.test
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class FullAccountAddressDataStateMachineImplTests : FunSpec({
  val spendingWallet = SpendingWalletMock(turbines::create)
  val registerWatchAddressProcessor =
    ProcessorMock<RegisterWatchAddressContext>(turbines::create)
  val stateMachine =
    FullAccountAddressDataStateMachineImpl(
      registerWatchAddressProcessor = registerWatchAddressProcessor,
      appSpendingWalletProvider = AppSpendingWalletProviderMock(spendingWallet)
    )

  val props = FullAccountAddressDataProps(FullAccountMock)

  beforeTest {
    spendingWallet.reset()
    registerWatchAddressProcessor.reset()
  }

  test("generate new address successfully") {
    stateMachine.test(props) {
      spendingWallet.newAddressResult = Ok(someBitcoinAddress)
      registerWatchAddressProcessor.processBatchReturnValues = listOf(Ok(Unit))

      with(awaitItem()) {
        latestAddress.shouldBeNull()
        generateAddress()
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

      with(awaitItem()) {
        latestAddress.shouldBe(someBitcoinAddress)
      }
    }
  }

  test("generate new address - failure") {
    stateMachine.test(props) {
      spendingWallet.newAddressResult = Ok(someBitcoinAddress)
      registerWatchAddressProcessor.processBatchReturnValues = listOf(Ok(Unit))

      with(awaitItem()) {
        latestAddress.shouldBeNull()
        generateAddress()
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

      with(awaitItem()) {
        latestAddress.shouldBe(someBitcoinAddress)
        spendingWallet.newAddressResult =
          Err(Generic(Exception("failed to generate address"), null))
        generateAddress()
      }

      // Latest address hasn't changed
      expectNoEvents()
    }
  }
})
