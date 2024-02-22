package build.wallet.functional.statemachine.data.sweep

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.onboarding.CreateAccountKeysetService
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.builder.KeyCrossBuilder
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratingPsbtsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.NoFundsFoundData
import build.wallet.statemachine.data.recovery.sweep.SweepData.PsbtsGeneratedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SigningAndBroadcastingSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepCompleteData
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import build.wallet.testing.shouldBeLoaded
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

class SweepDataStateMachineFunctionalTests : FunSpec() {
  lateinit var app: AppTester
  lateinit var account: FullAccount
  lateinit var stateMachine: SweepDataStateMachineImpl
  lateinit var createAccountKeysetService: CreateAccountKeysetService
  lateinit var keyCrossBuilder: KeyCrossBuilder
  lateinit var appKeysGenerator: AppKeysGenerator
  lateinit var appPrivateKeyDao: AppPrivateKeyDao
  lateinit var keyboxDao: KeyboxDao
  lateinit var appSpendingWalletProvider: AppSpendingWalletProvider

  init {
    beforeEach {
      app = launchNewApp()

      account = app.onboardFullAccountWithFakeHardware()
      createAccountKeysetService = app.app.createAccountKeysetService
      keyCrossBuilder = app.app.keyCrossBuilder
      appKeysGenerator = app.app.appKeysGenerator
      appPrivateKeyDao = app.app.appComponent.appPrivateKeyDao
      keyboxDao = app.app.appComponent.keyboxDao
      appSpendingWalletProvider = app.app.appComponent.appSpendingWalletProvider

      stateMachine =
        app.app.run {
          SweepDataStateMachineImpl(
            bitcoinBlockchain,
            sweepGenerator,
            mobilePaySigningService,
            appSpendingWalletProvider,
            exchangeRateSyncer,
            transactionRepository
          )
        }
    }

    test("sweep funds for account with no inactive keysets and recovered app key") {
      stateMachine.test(
        SweepDataProps(App, account.keybox, onSuccess = {}),
        useVirtualTime = false,
        turbineTimeout = 20.seconds
      ) {
        awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
        awaitItem().shouldBeTypeOf<NoFundsFoundData>()
      }
    }

    test("sweep funds for account with no inactive keysets and recovered hardware key") {
      stateMachine.test(
        SweepDataProps(Hardware, account.keybox, onSuccess = {}),
        useVirtualTime = false,
        turbineTimeout = 20.seconds
      ) {
        awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
        awaitItem().shouldBeTypeOf<NoFundsFoundData>()
      }
    }

    /**
     * This test
     * - Creates a new keybox
     * - Creates an inactive keyset (it pretends to be an old keyset
     *     that we rotated out of)
     * - Funds the inactive keyset using the treasury
     * - Does a sweep
     * - Checks that there's a balance in the active keybox
     * - Send the remaining money back to the treasury
     */
    test("sweep funds for lost hw recovery") {
      lateinit var lostKeyset: SpendingKeyset
      runTest {
        // There is a 15 second delay in this code path. Using the test dispatcher skips this delay.
        lostKeyset = createLostHardwareKeyset()
      }
      val wallet = appSpendingWalletProvider.getSpendingWallet(lostKeyset).getOrThrow()

      val funding = app.treasuryWallet.fund(wallet, BitcoinMoney.sats(10_000))
      println("Funded ${funding.depositAddress.address}")

      app.setupMobilePay(account, FiatMoney.usd(100.0))

      stateMachine.test(
        SweepDataProps(Hardware, account.keybox) {},
        useVirtualTime = false,
        testTimeout = 60.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
        val psbtsGeneratedData = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        psbtsGeneratedData.startSweep()
        awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()
        awaitItem().shouldBeTypeOf<SweepCompleteData>()

        val activeWallet =
          appSpendingWalletProvider.getSpendingWallet(
            account.keybox.activeSpendingKeyset
          ).getOrThrow()

        eventually(
          eventuallyConfig {
            duration = 20.seconds
            interval = 1.seconds
            initialDelay = 1.seconds
          }
        ) {
          activeWallet.sync().shouldBeOk()
          activeWallet.balance().first()
            .shouldBeLoaded()
            .total
            .shouldBe(BitcoinMoney.sats(10_000) - psbtsGeneratedData.totalFeeAmount)
        }
      }

      app.returnFundsToTreasury(account)
    }
  }

  private suspend fun createLostHardwareKeyset(): SpendingKeyset {
    val network = app.initialBitcoinNetworkType
    // Since we use mock hardware, a keyset that we've lost hardware for is equivalent to
    // a keyset that we've deleted the private keys for
    val newKeyBundle = appKeysGenerator.generateKeyBundle(network).getOrThrow()
    appPrivateKeyDao.remove(newKeyBundle.authKey)
    appPrivateKeyDao.remove(newKeyBundle.spendingKey)

    val hwKeyBundle =
      HwKeyBundle(
        localId = "fake-lost-hardware-key-bundle-id",
        spendingKey = HwSpendingPublicKey(newKeyBundle.spendingKey.key),
        authKey = HwAuthPublicKey(newKeyBundle.authKey.pubKey),
        networkType = network
      )

    val appKeyCross = keyCrossBuilder.createNewKeyCross(account.keybox.config)
    val appHwKeyCross = keyCrossBuilder.addHardwareKeyBundle(appKeyCross, hwKeyBundle)
    val response =
      createAccountKeysetService.createKeyset(
        account.config.f8eEnvironment,
        account.accountId,
        HwSpendingPublicKey(hwKeyBundle.spendingKey.key),
        appHwKeyCross.appKeyBundle.spendingKey,
        network,
        account.keybox.activeKeyBundle.authKey,
        app.getHardwareFactorProofOfPossession(account.keybox)
      ).getOrThrow()

    val keyset =
      keyCrossBuilder.addServerKey(
        appHwKeyCross,
        response
      ).spendingKeyset
    keyboxDao.saveKeyboxAsActive(
      account.keybox.copy(
        inactiveKeysets = (account.keybox.inactiveKeysets + keyset).toImmutableList()
      )
    ).getOrThrow()
    return keyset
  }
}
