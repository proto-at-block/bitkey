package build.wallet.onboarding

import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class CreateSoftwareWalletServiceComponentTests : FunSpec({

  context("happy path") {
    test("successfully create software account") {
      val app = launchNewApp()
      app.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)

      app.createSoftwareWalletService.createAccount().shouldBeOkOfType<SoftwareAccount>()
    }

    test("make bdk wallet") {
      val app = launchNewApp()
      app.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)
      val softwareAccount =
        app.createSoftwareWalletService.createAccount().shouldBeOkOfType<SoftwareAccount>()
      val wallet: WatchingWallet =
        app.keysetWalletProvider
          .getWatchingWallet(softwareAccount.keybox)
          .shouldBeOk()

      wallet.getNewAddress().shouldBeOk()

      app.treasuryWallet.fund(
        destinationWallet = wallet,
        amount = BitcoinMoney.sats(50_000L)
      )

      wallet
        .balance()
        .first()
        .total
        .shouldBe(BitcoinMoney.sats(50_000L))
    }
  }

  context("unhappy path") {
    test("workflow fails when an account already exists") {
      val app = launchNewApp()
      app.onboardFullAccountWithFakeHardware()

      app.createSoftwareWalletService.createAccount().shouldBeErrOfType<Error>()
    }

    test("workflow fails when feature flag is disabled") {
      val app = launchNewApp()
      app.softwareWalletIsEnabledFeatureFlag.setFlagValue(false)

      app.createSoftwareWalletService.createAccount().shouldBeErrOfType<Error>()
    }
  }
})
