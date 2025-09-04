package bitkey.recovery

import bitkey.auth.AuthTokenScope.Global
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth.WithDirectKeys
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getActiveHwAuthKey
import build.wallet.testing.ext.getHardwareFactorProofOfPossession
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.signChallengeWithHardware
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class LostAppAndCloudRecoveryFunctionalTests : FunSpec({
  test("authenticate with hardware and initiate recovery") {
    val oldApp = launchNewApp()
    val account = oldApp.onboardFullAccountWithFakeHardware()

    val newApp = launchNewApp(hardwareSeed = oldApp.fakeHardwareKeyStore.getSeed())

    // Initiate auth
    val hwAuthKey = newApp.getActiveHwAuthKey().publicKey
    val initiatedAuth = newApp.lostAppAndCloudRecoveryService.initiateAuth(hwAuthKey).shouldBeOk()
    initiatedAuth.accountId.shouldBe(account.accountId.serverId)
    initiatedAuth.username.shouldBe("${account.accountId.serverId}-hardware")

    // Auth tokens are not stored yet until we complete auth
    newApp.authTokensService.getTokens(account.accountId, Global).shouldBeOk(null)

    // Complete auth
    val signedChallenge = newApp.signChallengeWithHardware(initiatedAuth.challenge)
    val completedAuth = newApp.lostAppAndCloudRecoveryService.completeAuth(
      hwAuthKey = hwAuthKey,
      session = initiatedAuth.session,
      accountId = account.accountId,
      hwSignedChallenge = signedChallenge
    ).shouldBeOk()

    completedAuth.shouldBeInstanceOf<WithDirectKeys>()
    completedAuth.hwAuthKey.shouldBe(hwAuthKey)
    completedAuth.existingHwSpendingKeys.shouldContainExactly(
      account.keybox.activeHwKeyBundle.spendingKey
    )
    // TODO: test starting and completing recovery
  }

  test("attempt to cancel recovery when there is no active recovery - noop") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()
    val hwProofOfPossession = app.getHardwareFactorProofOfPossession()
    app.lostAppAndCloudRecoveryService
      .cancelRecovery(account.accountId, hwProofOfPossession)
      .shouldBeOk()
  }
})
