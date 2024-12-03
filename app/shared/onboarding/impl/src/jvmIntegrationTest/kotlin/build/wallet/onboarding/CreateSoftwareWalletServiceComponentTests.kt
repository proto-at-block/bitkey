package build.wallet.onboarding

import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class CreateSoftwareWalletServiceComponentTests : FunSpec({

  lateinit var app: AppTester
  lateinit var service: CreateSoftwareWalletService

  beforeTest {
    app = launchNewApp()
    service = app.createSoftwareWalletService
  }

  context("happy path") {
    xtest("successfully create software account") {
      app.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)

      service.createAccount().shouldBeOkOfType<SoftwareAccount>()
    }

    xtest("make bdk wallet") {
      app.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)
      val softwareAccount = service.createAccount().shouldBeOkOfType<SoftwareAccount>()
      val wallet: WatchingWallet =
        app.keysetWalletProvider.getWatchingWallet(softwareAccount.keybox)
          .shouldBeOk()

      wallet.getNewAddress().shouldBeOk()

      app.treasuryWallet.fund(
        destinationWallet = wallet,
        amount = BitcoinMoney.sats(50_000L)
      )

      wallet.balance().first()
        .total.shouldBe(BitcoinMoney.sats(50_000L))
    }
  }

  context("unhappy path") {

    test("workflow fails when an account already exists") {
      app.onboardFullAccountWithFakeHardware()

      service.createAccount().shouldBeErrOfType<Error>()
    }

    test("workflow fails when feature flag is disabled") {
      app.softwareWalletIsEnabledFeatureFlag.setFlagValue(false)

      service.createAccount().shouldBeErrOfType<Error>()
    }
  }
})
