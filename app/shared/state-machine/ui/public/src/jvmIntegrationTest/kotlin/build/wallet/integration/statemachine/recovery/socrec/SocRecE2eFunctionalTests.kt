package build.wallet.integration.statemachine.recovery.socrec

import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.relationships.*
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.f8e.socrec.endorseTrustedContacts
import build.wallet.f8e.socrec.getRelationships
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementProps
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.shouldHaveTrailingAccessoryButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.junit.jupiter.api.fail
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
      relationshipId = tcApp.awaitRelationships {
        !it.protectedCustomers.isEmpty()
      }
        .protectedCustomers
        .first()
        .relationshipId
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Wait for the cloud backup to contain the TC's SocRec PKEK
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)

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

    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

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
      tcApp.awaitRelationships {
        !it.protectedCustomers.isEmpty()
      }
      cancelAndIgnoreRemainingEvents()
    }

    // Attacker impersonating as PC: Endorse TC with a tampered key certificate
    val customerAccount = customerApp.getActiveFullAccount()
    val socRecF8eClient = customerApp.app.appComponent.socRecF8eClientProvider.get()
    val relationships = socRecF8eClient.getRelationships(
      customerAccount.accountId,
      customerAccount.config.f8eEnvironment
    ).getOrThrow()
    val unendorsedTc = relationships.unendorsedTrustedContacts
      .single()
    val socRecCrypto = customerApp.app.appComponent.socRecCrypto
    val badKeyCert = TrustedContactKeyCertificate(
      // This is a tampered key we are trying to get into the Protected Customer backup
      delegatedDecryptionKey = socRecCrypto.generateDelegatedDecryptionKey().getOrThrow().publicKey,
      hwAuthPublicKey = customerApp.getActiveHwAuthKey().publicKey,
      appGlobalAuthPublicKey = customerApp.getActiveAppGlobalAuthKey().publicKey,
      appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature("tampered-app-auth-key-sig"),
      trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignature("tampered-tc-identity-key-sig")
    )
    socRecF8eClient.endorseTrustedContacts(
      account = customerAccount,
      endorsements = listOf(
        TrustedContactEndorsement(
          RelationshipId(unendorsedTc.relationshipId),
          badKeyCert
        )
      )
    ).getOrThrow()
    // Sanity check that the TC has been successfully endorsed with the tampered key certificate
    socRecF8eClient.getRelationships(
      customerAccount.accountId,
      customerAccount.config.f8eEnvironment
    ).getOrThrow()
      .endorsedTrustedContacts
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

      customerApp.awaitRelationships {
        it.endorsedTrustedContacts.size == 1 &&
          it.endorsedTrustedContacts.single().authenticationState == TrustedContactAuthenticationState.TAMPERED
      }
      cancelAndIgnoreRemainingEvents()
    }
    backupJob.cancel()
  }

  xtest("socrec restore succeeds after cloud recovery without rotating auth keys") {
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
      customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

      customerApp = launchNewApp(
        cloudKeyValueStore = customerApp.app.cloudKeyValueStore,
        hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
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
      customerApp.readCloudBackup()
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
  xtest("socrec restore succeeds after cloud recovery and rotating auth keys") {
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
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

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
      awaitUntilScreenWithBody<FormBodyModel>(SUCCESSFULLY_ROTATED_AUTH)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)
    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  test("Lost App Cloud Recovery should succeed after PC does Lost Hardware D&N with Trusted Contact") {
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
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // PC: lost hardware D+N
    customerApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()
    customerApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(customerApp)
      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)

    // PC: Start up a fresh app with clean data, persist cloud backup and hardware
    //     Perform Lost App Cloud Recovery
    val hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
    val recoveringApp = launchNewApp(
      cloudStoreAccountRepository = customerApp.app.cloudStoreAccountRepository,
      cloudKeyValueStore = customerApp.app.cloudKeyValueStore,
      hardwareSeed = hardwareSeed
    )
    recoveringApp.app.appUiStateMachine.test(
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
      awaitUntilScreenWithBody<FormBodyModel>(SUCCESSFULLY_ROTATED_AUTH)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(recoveringApp)
  }

  test("social recovery should succeed after PC does lost app D&N") {
    // Onboard the protected customer
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
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // PC: lost app & cloud D+N
    val hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
    customerApp.deleteBackupsFromFakeCloud()
    customerApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()

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

    verifyKeyCertificatesAreRefreshed(customerApp)

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

      relationshipId = tcApp.awaitRelationships {
        !it.protectedCustomers.isEmpty()
      }
        .protectedCustomers
        .first()
        .relationshipId

      cancelAndIgnoreRemainingEvents()
    }

    // PC: Wait for the cloud backup to contain the TC's SocRec PKEK
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)

    // TC: lost app & cloud D+N
    val hardwareSeed = tcApp.fakeHardwareKeyStore.getSeed()
    tcApp.deleteBackupsFromFakeCloud()
    tcApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()

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
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)
  }

  test("social recovery should succeed after PC does lost hardware D&N") {
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
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // PC: lost hardware D+N
    customerApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()
    customerApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(customerApp)
      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)

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
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    val recoveredApp = shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)

    // PC: lost hardware D+N
    recoveredApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()
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

suspend fun buildTrustedContactManagementUiStateMachineProps(
  appTester: AppTester,
): TrustedContactManagementProps {
  return TrustedContactManagementProps(
    account = appTester.getActiveFullAccount(),
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
      is OnboardingSoftwareAccount -> fail("unexpected account type")
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

/**
 * Pulls SocRec relationships from F8e, ensures that the key certs match the ones we have locally,
 * and that local certs are verified.
 */
suspend fun verifyKeyCertificatesAreRefreshed(appTester: AppTester) {
  val account = appTester.getActiveFullAccount()
  val appPubKey = account.keybox.activeAppKeyBundle.authKey
  val hwSig = account.keybox.appGlobalAuthKeyHwSignature
  val hwPubKey = account.keybox.activeHwKeyBundle.authKey.pubKey
  hwPubKey.shouldBeEqual(appTester.fakeHardwareKeyStore.getAuthKeypair().publicKey.pubKey)

  val serverTcs = appTester.app.appComponent.socRecF8eClientProvider.get()
    .getRelationships(account).getOrThrow()
    .endorsedTrustedContacts
  val dbTcs = appTester.app.appComponent.socRecRelationshipsDao
    .socRecRelationships().first().getOrThrow()
    .endorsedTrustedContacts

  withClue("Server and DB Trusted Contacts should match") {
    serverTcs.shouldContainExactlyInAnyOrder(
      dbTcs.map {
        // The authenticationState is @Transient and defaults to AWAITING_VERIFY when returned by
        // the server
        it.copy(authenticationState = TrustedContactAuthenticationState.AWAITING_VERIFY)
      }
    )
  }

  dbTcs.forEach {
    withClue("key certificates for ${it.trustedContactAlias} should be refreshed") {
      it.authenticationState.shouldBe(TrustedContactAuthenticationState.VERIFIED)
      it.keyCertificate.hwAuthPublicKey.pubKey.shouldBeEqual(hwPubKey)
      it.keyCertificate.appGlobalAuthPublicKey.shouldBeEqual(appPubKey)
      it.keyCertificate.appAuthGlobalKeyHwSignature.value.shouldBeEqual(hwSig.value)
    }
  }
}
