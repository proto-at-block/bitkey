package build.wallet.functional.statemachine.data.sweep

import build.wallet.logging.logTesting
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratingPsbtsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.NoFundsFoundData
import build.wallet.statemachine.data.recovery.sweep.SweepData.PsbtsGeneratedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SigningAndBroadcastingSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepCompleteData
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.testing.ext.createLostHardwareKeyset
import build.wallet.testing.ext.getActiveWallet
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.ext.setupMobilePay
import build.wallet.testing.ext.testForBdk1AndBdk2
import build.wallet.testing.ext.testForLegacyAndPrivateWallet
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class SweepDataStateMachineFunctionalTests : FunSpec({
  // Legacy/Private wallet mode tests (existing)
  testForLegacyAndPrivateWallet("sweep funds for account with no inactive keysets and recovered app key") { app ->
    val account = app.onboardFullAccountWithFakeHardware()
    app.sweepDataStateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = account.keybox,
        onSuccess = {}
      ),
      turbineTimeout = 20.seconds
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<NoFundsFoundData>()
    }
  }

  testForLegacyAndPrivateWallet("sweep funds for account with no inactive keysets and recovered hardware key") { app ->
    val account = app.onboardFullAccountWithFakeHardware()
    app.sweepDataStateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = account.keybox,
        onSuccess = {}
      ),
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
  testForLegacyAndPrivateWallet("sweep funds for lost hw recovery", isIsolatedTest = true) { app ->
    val account = app.onboardFullAccountWithFakeHardware()
    val keyboxWithNewKeyset = app.createLostHardwareKeyset(account)
    val lostKeyset = keyboxWithNewKeyset.newKeyset
    val wallet = app.appSpendingWalletProvider.getSpendingWallet(lostKeyset).getOrThrow()

    val funding = app.treasuryWallet.fund(wallet, BitcoinMoney.sats(10_000))
    logTesting { "Funded ${funding.depositAddress.address}" }

    app.setupMobilePay(FiatMoney.usd(100.0))

    app.sweepDataStateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = keyboxWithNewKeyset.keybox,
        onSuccess = {}
      ),
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

  // BDK version matrix tests
  testForBdk1AndBdk2("sweep funds for account with no inactive keysets and recovered app key") { app ->
    val account = app.onboardFullAccountWithFakeHardware()
    app.sweepDataStateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = account.keybox,
        onSuccess = {}
      ),
      turbineTimeout = 20.seconds
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<NoFundsFoundData>()
    }
  }

  testForBdk1AndBdk2("sweep funds for account with no inactive keysets and recovered hardware key") { app ->
    val account = app.onboardFullAccountWithFakeHardware()
    app.sweepDataStateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = account.keybox,
        onSuccess = {}
      ),
      turbineTimeout = 20.seconds
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<NoFundsFoundData>()
    }
  }

  testForBdk1AndBdk2("sweep funds for lost hw recovery", isIsolatedTest = true) { app ->
    val account = app.onboardFullAccountWithFakeHardware()
    val keyboxWithNewKeyset = app.createLostHardwareKeyset(account)
    val lostKeyset = keyboxWithNewKeyset.newKeyset
    val wallet = app.appSpendingWalletProvider.getSpendingWallet(lostKeyset).getOrThrow()

    val funding = app.treasuryWallet.fund(wallet, BitcoinMoney.sats(10_000))
    logTesting { "Funded ${funding.depositAddress.address}" }

    app.setupMobilePay(FiatMoney.usd(100.0))

    app.sweepDataStateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = keyboxWithNewKeyset.keybox,
        onSuccess = {}
      ),
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
})
