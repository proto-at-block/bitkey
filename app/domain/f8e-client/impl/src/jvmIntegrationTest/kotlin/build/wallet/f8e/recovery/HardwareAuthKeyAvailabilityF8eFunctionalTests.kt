package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.HardwareAuthKeyAvailabilityErrorCode
import bitkey.f8e.error.code.HardwareAuthKeyAvailabilityErrorCode.HW_AUTH_PUBKEY_IN_USE
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HardwareAuthKeyAvailabilityF8eFunctionalTests : FunSpec({
  test("availability check allows unused and current-account hardware auth keys") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()
    val unusedHwAuthKey = HwAuthPublicKey(
      app.secp256k1KeyGenerator.generateKeypair().publicKey
    )

    app.checkHardwareAuthKeyAvailability(account, unusedHwAuthKey)
      .shouldBeOk(HardwareAuthKeyAvailabilityStatus.Available)

    app.checkHardwareAuthKeyAvailability(account, account.keybox.activeHwKeyBundle.authKey)
      .shouldBeOk(HardwareAuthKeyAvailabilityStatus.ActiveOnCurrentAccount)
  }

  test("availability check rejects hardware auth key active on another account") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()
    val otherApp = launchNewApp()
    val otherAccount = otherApp.onboardFullAccountWithFakeHardware()

    val error = app.checkHardwareAuthKeyAvailability(account, otherAccount.keybox.activeHwKeyBundle.authKey)
      .shouldBeErrOfType<F8eError.SpecificClientError<HardwareAuthKeyAvailabilityErrorCode>>()

    error.errorCode.shouldBe(HW_AUTH_PUBKEY_IN_USE)
  }
})

private suspend fun AppTester.checkHardwareAuthKeyAvailability(
  account: FullAccount,
  hardwareAuthPublicKey: HwAuthPublicKey,
) = hardwareAuthKeyAvailabilityF8eClient.checkAvailability(
  f8eEnvironment = account.config.f8eEnvironment,
  fullAccountId = account.accountId,
  hardwareAuthPublicKey = hardwareAuthPublicKey,
  appAuthKey = account.keybox.activeAppKeyBundle.authKey
)
