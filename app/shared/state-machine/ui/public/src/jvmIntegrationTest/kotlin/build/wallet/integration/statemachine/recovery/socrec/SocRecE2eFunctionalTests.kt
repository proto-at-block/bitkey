@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, ExperimentalKotest::class)

package build.wallet.integration.statemachine.recovery.socrec

import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.socrec.RecoveryRelationshipId
import build.wallet.bitkey.socrec.TcIdentityKeyAppSignature
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.TrustedContactEndorsement
import build.wallet.bitkey.socrec.TrustedContactKeyCertificate
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.f8e.socrec.endorseTrustedContacts
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import build.wallet.recovery.socrec.syncAndVerifyRelationships
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementProps
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.shouldHaveTrailingAccessoryButton
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.fail
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.junit.jupiter.api.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val PROTECTED_CUSTOMER_ALIAS = "alice"

class SocRecE2eFunctionalTests : FunSpec({
  test("full e2e test") {
    // Onboard the protected customer
    val customerApp = launchNewApp()
    val cloudStore = customerApp.app.cloudKeyValueStore
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
    val tcApp = launchNewApp(cloudKeyValueStore = cloudStore)
    lateinit var relationshipId: String
    tcApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      advanceThroughCreateLiteAccountScreens(inviteCode, CloudStoreAccountFake.TrustedContactFake)
      advanceThroughTrustedContactEnrollmentScreens(PROTECTED_CUSTOMER_ALIAS)
      relationshipId = tcApp.app.socRecRelationshipsRepository.awaitRelationships {
        !it.protectedCustomers.isEmpty()
      }
        .protectedCustomers
        .first()
        .recoveryRelationshipId
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Wait for the cloud backup to contain the TC's SocRec PKEK
    customerApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      customerApp.app.socRecRelationshipsRepository.awaitRelationships {
        it.unendorsedTrustedContacts.size == 1 &&
          it.unendorsedTrustedContacts.single().authenticationState == TrustedContactAuthenticationState.UNAUTHENTICATED
      }
      customerApp.app.socRecRelationshipsRepository.awaitRelationships {
        it.trustedContacts.size == 1 &&
          it.trustedContacts.single().authenticationState == TrustedContactAuthenticationState.VERIFIED
      }
      customerApp.awaitCloudBackupRefreshed(relationshipId)
      cancelAndIgnoreRemainingEvents()
    }

    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  /**
   * This test is a sanity check to ensure that the AppTester methods for fixturing socrec
   * enrollment produce the same results as walking through the flows. The shortcut methods allow
   * for more compact tests with less noise.
   */
  test("AppTester shortcuts for socrec") {
    var customerApp = launchNewApp()
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")

    val tcApp = launchNewApp()
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )

    customerApp.endorseAndVerifyTc(invite.invitation.recoveryRelationshipId)

    customerApp = launchNewApp(
      cloudKeyValueStore = customerApp.app.cloudKeyValueStore
    )
    customerApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      turbineTimeout = 5.seconds
    ) {
      // Expect the social recovery button to exist
      advanceToCloudRecovery().shouldHaveTrailingAccessoryButton()
    }
  }

  // If this test flakes, it likely points to a real issue. See comments in test.
  test("attacker can't fake an endorsement") {
    // Onboard the protected customer
    val customerApp = launchNewApp()
    val cloudStore = customerApp.app.cloudKeyValueStore
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
    val tcApp = launchNewApp(cloudKeyValueStore = cloudStore)
    tcApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      advanceThroughCreateLiteAccountScreens(inviteCode, CloudStoreAccountFake.TrustedContactFake)
      advanceThroughTrustedContactEnrollmentScreens(PROTECTED_CUSTOMER_ALIAS)
      tcApp.app.socRecRelationshipsRepository.awaitRelationships {
        !it.protectedCustomers.isEmpty()
      }
      cancelAndIgnoreRemainingEvents()
    }

    // Attacker impersonating as PC: Endorse TC with a tampered key certificate
    val customerAccount = customerApp.getActiveFullAccount()
    val socRecService = customerApp.app.socialRecoveryServiceProvider.get()
    val relationships = socRecService.getRelationships(
      customerAccount.accountId,
      customerAccount.config.f8eEnvironment,
      null
    ).getOrThrow()
    val unendorsedTc = relationships.unendorsedTrustedContacts
      .single()
    val socRecCrypto = customerApp.app.socRecCrypto
    val badKeyCert = TrustedContactKeyCertificate(
      // This is a tampered key we are trying to get into the Protected Customer backup
      delegatedDecryptionKey = socRecCrypto.generateDelegatedDecryptionKey().getOrThrow(),
      hwAuthPublicKey = customerApp.getActiveHwAuthKey().publicKey,
      appGlobalAuthPublicKey = customerApp.getActiveAppGlobalAuthKey().publicKey,
      appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature("tampered-app-auth-key-sig"),
      trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignature("tampered-tc-identity-key-sig")
    )
    socRecService.endorseTrustedContacts(
      account = customerAccount,
      endorsements = listOf(
        TrustedContactEndorsement(
          RecoveryRelationshipId(unendorsedTc.recoveryRelationshipId),
          badKeyCert
        )
      )
    ).getOrThrow()
    // Sanity check that the TC has been successfully endorsed with the tampered key certificate
    socRecService.getRelationships(
      customerAccount.accountId,
      customerAccount.config.f8eEnvironment,
      null
    ).getOrThrow()
      .trustedContacts
      .shouldBeSingleton()

    // Detect whether the Trusted Contact gets written to cloud backup even though it's in the
    // TAMPERED state. Since the Trusted Contact verification check is running in the background,
    // we may not catch a potential issue where the tampered Trusted Contact does get backed up
    // and then immediately removed. If that does happen, this test may likely flake, but
    // point to a real issue.
    val backupJob = launch {
      customerApp.app.cloudBackupDao.backup(customerAccount.accountId.serverId)
        .filterNotNull()
        .collect { backup ->
          if (backup.socRecDataAvailable) {
            fail { "Trusted Contact with tampered certificate should not have been backed up" }
          }
        }
    }

    // PC: Wait for the TC to end up in the tampered state.
    // Use turbineScope since we need to use testIn() when working with multiple turbines
    customerApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      customerApp.app.socRecRelationshipsRepository.awaitRelationships {
        it.trustedContacts.size == 1 &&
          it.trustedContacts.single().authenticationState == TrustedContactAuthenticationState.TAMPERED
      }
      cancelAndIgnoreRemainingEvents()
    }
    backupJob.cancel()
  }

  test("socrec restore succeeds after cloud recovery without rotating auth keys") {
    // Retry flaky test
    checkAll<Int>(
      PropTestConfig(
        iterations = 3,
        // Yes, both of these have to be set :(
        minSuccess = 2,
        maxFailure = 1
      )
    ) { _ ->
      var customerApp = launchNewApp()
      customerApp.onboardFullAccountWithFakeHardware(
        cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
      )
      val invite = customerApp.createTcInvite("bob")
      val tcApp = launchNewApp()
      tcApp.onboardLiteAccountFromInvitation(
        invite.inviteCode,
        PROTECTED_CUSTOMER_ALIAS,
        CloudStoreAccountFake.TrustedContactFake
      )
      customerApp.endorseAndVerifyTc(invite.invitation.recoveryRelationshipId)

      customerApp = launchNewApp(
        cloudKeyValueStore = customerApp.app.cloudKeyValueStore
      )
      customerApp.app.appUiStateMachine.test(
        props = Unit,
        useVirtualTime = false,
        turbineTimeout = 5.seconds
      ) {
        val cloudRecoveryForm = advanceToCloudRecovery()
        // Expect the social recovery button to exist
        cloudRecoveryForm.shouldHaveTrailingAccessoryButton()
        // Do cloud recovery
        cloudRecoveryForm.clickPrimaryButton()
        // Skip auth key rotation
        awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<MoneyHomeBodyModel>()

        // Suspend until a cloud backup refresh happens
        customerApp.app.cloudBackupRefresher.lastCheck
          .filter { it > Instant.DISTANT_PAST }
          .first()

        cancelAndIgnoreRemainingEvents()
      }

      // Expect the cloud backup to continue to contain the TC backup.
      customerApp.app.cloudBackupRefresher.lastCheck.value.shouldBeGreaterThan(Instant.DISTANT_PAST)
      customerApp.app.cloudBackupDao.get(customerApp.getActiveFullAccount().accountId.serverId)
        .getOrThrow()
        .shouldNotBeNull()
        .socRecDataAvailable
        .shouldBeTrue()

      // PC: Start up a fresh app with clean data and start Social Challenge
      // persist cloud account stores
      val recoveringApp = launchNewApp(
        cloudStoreAccountRepository = customerApp.app.cloudStoreAccountRepository,
        cloudKeyValueStore = customerApp.app.cloudKeyValueStore
      )
      recoveringApp.app.appUiStateMachine.test(
        props = Unit,
        useVirtualTime = false,
        turbineTimeout = 5.seconds
      ) {
        val cloudRecoveryForm = advanceToCloudRecovery()
        // Expect the social recovery button to exist
        cloudRecoveryForm.shouldHaveTrailingAccessoryButton()
      }

      shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
    }
  }

  // TODO: This test can hang but it's still very useful
  test("socrec restore succeeds after cloud recovery and rotating auth keys") {
    var customerApp = launchNewApp()
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
    val invite = customerApp.createTcInvite("bob")
    val tcApp = launchNewApp()
    tcApp.onboardLiteAccountFromInvitation(
      inviteCode = invite.inviteCode,
      protectedCustomerName = PROTECTED_CUSTOMER_ALIAS,
      cloudStoreAccountForBackup = CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.endorseAndVerifyTc(invite.invitation.recoveryRelationshipId)

    customerApp = launchNewApp(
      cloudKeyValueStore = customerApp.app.cloudKeyValueStore,
      hardwareSeed = hardwareSeed
    )
    customerApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      turbineTimeout = 5.seconds
    ) {
      advanceToCloudRecovery().clickPrimaryButton()
      // Do auth key rotation
      screenDecideIfShouldRotate {
        clickSecondaryButton()
      }
      // Note that the auth rotation should write a new cloud backup with new socrec keys.
      awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  test("social recovery should succeed after PC does lost app D&N") {
    // Onboard the protected customer
    var customerApp = launchNewApp()
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    val relationshipId = invite.invitation.recoveryRelationshipId
    val tcApp = launchNewApp()
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.endorseAndVerifyTc(invite.invitation.recoveryRelationshipId)

    // PC: lost app & cloud D+N
    val hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
    customerApp.deleteBackupsFromFakeCloud()
    customerApp.fakeNfcCommands.clearHardwareKeys()

    customerApp = launchNewApp(hardwareSeed = hardwareSeed)
    customerApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughLostAppAndCloudRecoveryToMoneyHome(CloudStoreAccountFake.ProtectedCustomerFake)
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Re-verify the TC key cert
    // TODO: https://linear.app/squareup/issue/BKR-1087
    //   This is a workaround for the race where the endorsement is not being re-verified in
    //   initial sync.
    val customerAccount = customerApp.getActiveFullAccount()
    customerApp.app.socRecRelationshipsRepository.syncAndVerifyRelationships(customerAccount)
      .getOrThrow()
      .trustedContacts
      .first { it.recoveryRelationshipId == relationshipId }
      .authenticationState
      .shouldBe(TrustedContactAuthenticationState.VERIFIED)

    customerApp.app.bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackup(
      customerAccount
    ).getOrThrow()
    customerApp.app.cloudBackupDao.get(customerAccount.accountId.serverId).getOrThrow()
      .shouldNotBeNull()
      .shouldBeTypeOf<CloudBackupV2>()
      .fullAccountFields.shouldNotBeNull()
      .socRecSealedDekMap
      .shouldHaveKey(relationshipId)
    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  test("social recovery should not be possible after TC does lost app D&N") {
    // Onboard the protected customer
    val customerApp = launchNewApp()
    val cloudStore = customerApp.app.cloudKeyValueStore
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    var tcApp = launchNewApp(cloudKeyValueStore = cloudStore)
    tcApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake(identifier = "cloud-store-protected-customer2-fake")
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

    // TC: accepts invite
    lateinit var relationshipId: String
    tcApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      advanceThroughFullAccountAcceptTCInviteScreens(inviteCode, PROTECTED_CUSTOMER_ALIAS)

      relationshipId = tcApp.app.socRecRelationshipsRepository.awaitRelationships {
        !it.protectedCustomers.isEmpty()
      }
        .protectedCustomers
        .first()
        .recoveryRelationshipId

      cancelAndIgnoreRemainingEvents()
    }

    // PC: Wait for the cloud backup to contain the TC's SocRec PKEK
    customerApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      customerApp.app.socRecRelationshipsRepository.awaitRelationships {
        it.unendorsedTrustedContacts.size == 1 &&
          it.unendorsedTrustedContacts.single().authenticationState == TrustedContactAuthenticationState.UNAUTHENTICATED
      }
      customerApp.app.socRecRelationshipsRepository.awaitRelationships {
        it.trustedContacts.size == 1 &&
          it.trustedContacts.single().authenticationState == TrustedContactAuthenticationState.VERIFIED
      }
      customerApp.awaitCloudBackupRefreshed(relationshipId)
      cancelAndIgnoreRemainingEvents()
    }

    // TC: lost app & cloud D+N
    val hardwareSeed = tcApp.fakeHardwareKeyStore.getSeed()
    tcApp.deleteBackupsFromFakeCloud()
    tcApp.fakeNfcCommands.clearHardwareKeys()

    tcApp = launchNewApp(hardwareSeed = hardwareSeed)
    tcApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughLostAppAndCloudRecoveryToMoneyHome(CloudStoreAccountFake.ProtectedCustomerFake)
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Wait for the cloud backup to be updated with no trusted contacts
    customerApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      customerApp.app.socRecRelationshipsRepository.awaitRelationships {
        it.trustedContacts.isEmpty()
      }
      customerApp.awaitCloudBackupRefreshed(relationshipId)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("social recovery should succeed after PC does lost hardware D&N") {
    // Onboard the protected customer
    val customerApp = launchNewApp()
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    val relationshipId = invite.invitation.recoveryRelationshipId
    val tcApp = launchNewApp()
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.endorseAndVerifyTc(invite.invitation.recoveryRelationshipId)

    // PC: lost hardware D+N
    customerApp.fakeNfcCommands.clearHardwareKeys()
    customerApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(customerApp)
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Re-verify the TC key cert
    // TODO: https://linear.app/squareup/issue/BKR-1087
    //   This is a workaround for the race where the endorsement is not being re-verified in
    //   initial sync.
    val customerAccount = customerApp.getActiveFullAccount()
    customerApp.app.socRecRelationshipsRepository.syncAndVerifyRelationships(customerAccount)
      .getOrThrow()
      .trustedContacts
      .first { it.recoveryRelationshipId == relationshipId }
      .authenticationState
      .shouldBe(TrustedContactAuthenticationState.VERIFIED)

    customerApp.app.bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackup(
      customerAccount
    ).getOrThrow()
    customerApp.app.cloudBackupDao.get(customerAccount.accountId.serverId).getOrThrow()
      .shouldNotBeNull()
      .shouldBeTypeOf<CloudBackupV2>()
      .fullAccountFields.shouldNotBeNull()
      .socRecSealedDekMap
      .shouldHaveKey(relationshipId)
    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  test("lost hardware D&N should succeed after social recovery") {
    // Onboard the protected customer
    val customerApp = launchNewApp()
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    val tcApp = launchNewApp()
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.endorseAndVerifyTc(invite.invitation.recoveryRelationshipId)

    val recoveredApp = shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)

    // PC: lost hardware D+N
    recoveredApp.fakeNfcCommands.clearHardwareKeys()
    recoveredApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(recoveredApp)
      cancelAndIgnoreRemainingEvents()
    }
  }
})

private suspend fun SocRecRelationshipsRepository.awaitRelationships(
  timeout: Duration = 3.seconds,
  predicate: (SocRecRelationships) -> Boolean,
): SocRecRelationships =
  relationships
    .transform { relationships ->
      if (predicate(relationships)) {
        emit(relationships)
      }
    }
    .timeout(timeout)
    .first()

fun AppTester.getSharedInviteCode(): String {
  val sharedText = lastSharedText.shouldNotBeNull()
  return """INVITE CODE:\s*(\S+)""".toRegex()
    .find(sharedText.text)
    .shouldNotBeNull()
    .groupValues[1]
}

suspend fun buildTrustedContactManagementUiStateMachineProps(
  appTester: AppTester,
): TrustedContactManagementProps {
  val repository = appTester.app.socRecRelationshipsRepository
  val account = appTester.getActiveFullAccount()
  val relationships = repository.syncAndVerifyRelationships(account).getOrThrow()
  return TrustedContactManagementProps(
    account = appTester.getActiveFullAccount(),
    socRecRelationships = relationships,
    socRecActions = repository.toActions(account),
    onExit = { fail("unexpected exit") }
  )
}

suspend fun shouldSucceedSocialRestore(
  customerApp: AppTester,
  tcApp: AppTester,
  protectedCustomerAlias: String,
): AppTester {
  // PC: Start up a fresh app with clean data and start Social Challenge
  // persist cloud account stores
  val recoveringApp = launchNewApp(
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
  tcApp.app.appUiStateMachine.test(
    props = Unit,
    useVirtualTime = false
  ) {
    when (tcApp.getActiveAccount()) {
      is LiteAccount -> advanceThroughSocialChallengeVerifyScreensAsLiteAccount(
        protectedCustomerAlias,
        challengeCode
      )
      is FullAccount -> advanceThroughSocialChallengeVerifyScreensAsFullAccount(
        protectedCustomerAlias,
        challengeCode
      )
    }
    cancelAndIgnoreRemainingEvents()
  }

  // PC: Complete Social Restore, make
  recoveringApp.app.appUiStateMachine.test(props = Unit, useVirtualTime = false) {
    val tcList =
      advanceToSocialChallengeTrustedContactList(CloudStoreAccountFake.ProtectedCustomerFake)
    advanceFromSocialRestoreToLostHardwareRecovery(tcList)
    cancelAndIgnoreRemainingEvents()
  }

  return recoveringApp
}
