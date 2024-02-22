package build.wallet.integration.statemachine.recovery.socrec

import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementProps
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile

class SocRecE2eFunctionalTests : FunSpec({
  test("full e2e test") {
    // Onboard the protected customer
    val customerApp = launchNewApp()
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    // PC: Invite a Trusted Contact
    customerApp.app.trustedContactManagementUiStateMachine.test(
      props = buildTrustedContactManagementUiStateMachineProps(customerApp),
      useVirtualTime = false
    ) {
      advanceThroughTrustedContactInviteScreens("bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()

    // TC: Onboard as Lite Account and accept invite
    val tcApp = launchNewApp()
    tcApp.app.cloudBackupRepository.clear(CloudStoreAccountFake.TrustedContactFake)
    lateinit var relationshipId: String
    tcApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      advanceThroughCreateLiteAccountScreens(inviteCode, CloudStoreAccountFake.TrustedContactFake)
      advanceThroughTrustedContactEnrollmentScreens("alice")
      relationshipId =
        tcApp.app.socRecRelationshipsRepository.relationships
          .transformWhile { relationships ->
            val found = !relationships.protectedCustomers.isEmpty()
            if (found) {
              emit(relationships)
            }
            !found
          }
          .first()
          .protectedCustomers
          .first()
          .recoveryRelationshipId
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Wait for the cloud backup to contain the TC's SocRec PKEK
    // TODO(BKR-825): Remove direct use of cloudStoreAccountRepository
    (customerApp.app.cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
      .set(CloudStoreAccountFake.ProtectedCustomerFake)
    customerApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
      awaitCloudBackupRefreshed(customerApp, relationshipId)
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Start up a fresh app with clean data and start Social Challenge
    // persist cloud account stores
    val recoveringApp = launchNewApp(
      cloudStoreAccountRepository = customerApp.app.cloudStoreAccountRepository,
      cloudKeyValueStore = customerApp.app.cloudKeyValueStore
    )
    lateinit var challengeCode: String
    recoveringApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      val tcList =
        advanceToSocialChallengeTrustedContactList(CloudStoreAccountFake.ProtectedCustomerFake)
      challengeCode = startSocialChallenge(tcList)
      cancelAndIgnoreRemainingEvents()
    }

    // TC: Enter the challenge code and upload ciphertext
    // TODO(BKR-825): Remove direct use of cloudStoreAccountRepository
    (recoveringApp.app.cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
      .set(CloudStoreAccountFake.TrustedContactFake)
    tcApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      advanceThroughSocialChallengeVerifyScreens(challengeCode)
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Complete Social Restore, make
    // TODO(BKR-825): Remove direct use of cloudStoreAccountRepository
    (recoveringApp.app.cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
      .set(CloudStoreAccountFake.ProtectedCustomerFake)
    recoveringApp.app.appUiStateMachine.test(props = Unit, useVirtualTime = false) {
      val tcList =
        advanceToSocialChallengeTrustedContactList(CloudStoreAccountFake.ProtectedCustomerFake)
      advanceFromSocialRestoreToLostHardwareRecovery(tcList)
      cancelAndIgnoreRemainingEvents()
    }
  }
})

private fun AppTester.getSharedInviteCode(): String {
  val sharedText = lastSharedText.shouldNotBeNull()
  return """invite code: (\S+)\.""".toRegex()
    .find(sharedText.text)
    .shouldNotBeNull()
    .groupValues[1]
}

private suspend fun buildTrustedContactManagementUiStateMachineProps(
  appTester: AppTester,
): TrustedContactManagementProps {
  val repository = appTester.app.socRecRelationshipsRepository
  val account = appTester.getActiveFullAccount()
  val relationships =
    repository.syncRelationships(
      account.accountId,
      account.config.f8eEnvironment
    ).getOrThrow()
  return TrustedContactManagementProps(
    account = appTester.getActiveFullAccount(),
    socRecRelationships = relationships,
    socRecActions = repository.toActions(account),
    onExit = { fail("unexpected exit") }
  )
}
