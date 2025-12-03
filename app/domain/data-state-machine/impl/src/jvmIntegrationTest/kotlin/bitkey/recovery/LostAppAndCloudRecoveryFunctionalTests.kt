package bitkey.recovery

import bitkey.auth.AuthTokenScope.Global
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth.WithDescriptorBackups
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth.WithDirectKeys
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getActiveHwAuthKey
import build.wallet.testing.ext.getHardwareFactorProofOfPossession
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.signChallengeWithHardware
import build.wallet.testing.ext.testWithTwoApps
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class LostAppAndCloudRecoveryFunctionalTests : FunSpec({
  testWithTwoApps("authenticate with hardware and initiate recovery") { oldApp, newApp ->
    val account = oldApp.onboardFullAccountWithFakeHardware()

    val hardwareSeed = oldApp.fakeHardwareKeyStore.getSeed()
    newApp.fakeHardwareKeyStore.setSeed(hardwareSeed)

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

    when (completedAuth) {
      is WithDescriptorBackups -> {
        completedAuth.hwAuthKey.shouldBe(hwAuthKey)
      }
      is WithDirectKeys -> {
        completedAuth.hwAuthKey.shouldBe(hwAuthKey)
        completedAuth.existingHwSpendingKeys.shouldContainExactly(
          account.keybox.activeHwKeyBundle.spendingKey
        )
      }
    }
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
