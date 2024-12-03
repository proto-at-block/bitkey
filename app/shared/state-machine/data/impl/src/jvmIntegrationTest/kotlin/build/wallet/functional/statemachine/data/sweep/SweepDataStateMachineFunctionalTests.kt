package build.wallet.functional.statemachine.data.sweep

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.encrypt.toSecp256k1PublicKey
import build.wallet.logging.logTesting
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.sweep.SweepData.*
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachine
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.IsolatedTest
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
  lateinit var stateMachine: SweepDataStateMachine

  init {
    beforeEach {
      app = launchNewApp()

      account = app.onboardFullAccountWithFakeHardware()
      stateMachine = app.sweepDataStateMachine
    }

    test("sweep funds for account with no inactive keysets and recovered app key") {
      stateMachine.test(
        SweepDataProps(account.keybox, onSuccess = {}),
        useVirtualTime = false,
        turbineTimeout = 20.seconds
      ) {
        awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
        awaitItem().shouldBeTypeOf<NoFundsFoundData>()
      }
    }

    test("sweep funds for account with no inactive keysets and recovered hardware key") {
      stateMachine.test(
        SweepDataProps(account.keybox, onSuccess = {}),
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
    test("sweep funds for lost hw recovery")
      .config(tags = setOf(IsolatedTest)) {
        lateinit var lostKeyset: SpendingKeyset
        runTest {
          // There is a 15 second delay in this code path. Using the test dispatcher skips this delay.
          lostKeyset = createLostHardwareKeyset()
        }
        val wallet = app.appSpendingWalletProvider.getSpendingWallet(lostKeyset).getOrThrow()

        val funding = app.treasuryWallet.fund(wallet, BitcoinMoney.sats(10_000))
        logTesting { "Funded ${funding.depositAddress.address}" }

        app.setupMobilePay(FiatMoney.usd(100.0))

        stateMachine.test(
          SweepDataProps(account.keybox) {},
          useVirtualTime = false,
          testTimeout = 60.seconds,
          turbineTimeout = 10.seconds
        ) {
          awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
          val psbtsGeneratedData = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
          psbtsGeneratedData.startSweep()
          awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()
          awaitItem().shouldBeTypeOf<SweepCompleteData>()

          val activeWallet = app.getActiveWallet()

          eventually(
            eventuallyConfig {
              duration = 20.seconds
              interval = 1.seconds
              initialDelay = 1.seconds
            }
          ) {
            activeWallet.sync().shouldBeOk()
            activeWallet.balance().first()
              .total
              .shouldBe(BitcoinMoney.sats(10_000) - psbtsGeneratedData.totalFeeAmount)
          }
        }

        app.returnFundsToTreasury()
      }
  }

  private suspend fun createLostHardwareKeyset(): SpendingKeyset {
    val network = app.initialBitcoinNetworkType
    // Since we use mock hardware, a keyset that we've lost hardware for is equivalent to
    // a keyset that we've deleted the private keys for
    val newKeyBundle = app.appKeysGenerator.generateKeyBundle(network).getOrThrow()
    app.appPrivateKeyDao.remove(newKeyBundle.authKey)
    app.appPrivateKeyDao.remove(newKeyBundle.spendingKey)

    val hwKeyBundle =
      HwKeyBundle(
        localId = "fake-lost-hardware-key-bundle-id",
        spendingKey = HwSpendingPublicKey(newKeyBundle.spendingKey.key),
        authKey = HwAuthPublicKey(newKeyBundle.authKey.toSecp256k1PublicKey()),
        networkType = network
      )

    val appKeyBundle = app.appKeysGenerator.generateKeyBundle(network).getOrThrow()

    val f8eSpendingKeyset =
      app.createAccountKeysetF8eClient
        .createKeyset(
          f8eEnvironment = account.config.f8eEnvironment,
          fullAccountId = account.accountId,
          hardwareSpendingKey = HwSpendingPublicKey(hwKeyBundle.spendingKey.key),
          appSpendingKey = appKeyBundle.spendingKey,
          network = network,
          appAuthKey = account.keybox.activeAppKeyBundle.authKey,
          hardwareProofOfPossession = app.getHardwareFactorProofOfPossession()
        )
        .getOrThrow()

    val keyset = SpendingKeyset(
      localId = "fake-spending-keyset-id",
      appKey = appKeyBundle.spendingKey,
      networkType = network,
      hardwareKey = hwKeyBundle.spendingKey,
      f8eSpendingKeyset = f8eSpendingKeyset
    )

    app.keyboxDao.saveKeyboxAsActive(
      account.keybox.copy(inactiveKeysets = (account.keybox.inactiveKeysets + keyset).toImmutableList())
    ).getOrThrow()
    return keyset
  }
}
