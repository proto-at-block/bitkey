package build.wallet.integration.statemachine.recovery.socrec

import bitkey.account.HardwareType
import bitkey.ui.framework.test
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.relationships.*
import build.wallet.cloud.backup.CloudBackupStore
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.f8e.relationships.endorseTrustedContacts
import build.wallet.f8e.relationships.getRelationships
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.recovery.sweep.ZeroBalancePromptBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
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
import kotlin.time.Duration.Companion.seconds

private const val PROTECTED_CUSTOMER_ALIAS = "alice"

@Suppress("LargeClass")
class SocRecE2eFunctionalTests : FunSpec({
  testWithTwoApps(
    "full e2e test",
    app1Factory = { mode ->
      when (mode) {
        AppMode.Legacy -> launchLegacyWalletApp(executeWorkers = false)
        AppMode.Private -> launchNewApp(executeWorkers = false)
      }
    },
    app2Factory = { customerApp, mode ->
      when (mode) {
        AppMode.Legacy -> launchLegacyWalletApp(
          cloudBackupStore = customerApp.cloudBackupStore,
          executeWorkers = false
        )
        AppMode.Private -> launchNewApp(
          cloudBackupStore = customerApp.cloudBackupStore,
          executeWorkers = false
        )
      }
    }
  ) { customerApp, tcApp ->
    // Onboard the protected customer
    // TODO(W-9704): execute workers by default
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    // PC: Invite a Trusted Contact
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()

    // TC: Onboard as Lite Account and accept invite
    // TODO(W-9704): execute workers by default
    lateinit var relationshipId: String
    tcApp.appUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = Unit
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

  test("full e2e test [W3 protected customer]") {
    val customerApp = launchNewApp(executeWorkers = false)
    val tcApp = launchNewApp(
      cloudBackupStore = customerApp.cloudBackupStore,
      executeWorkers = false
    )

    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake,
      hardwareType = HardwareType.W3
    )
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob", HardwareCoverageMode.W3Private)
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()

    lateinit var relationshipId: String
    tcApp.appUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = Unit
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

    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)
    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  /**
   * This test is a sanity check to ensure that the AppTester methods for fixturing socrec
   * enrollment produce the same results as walking through the flows. The shortcut methods allow
   * for more compact tests with less noise.
   */
  test("AppTester shortcuts for socrec") {
    // TODO(W-9704): execute workers by default
    var customerApp = launchNewApp(executeWorkers = false)
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")

    // TODO(W-9704): execute workers by default
    val tcApp = launchNewApp(executeWorkers = false)
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )

    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // TODO(W-9704): execute workers by default
    customerApp = launchNewApp(
      cloudBackupStore = customerApp.cloudBackupStore,
      executeWorkers = false
    )
    customerApp.appUiStateMachine.test(
      props = Unit,
      turbineTimeout = 60.seconds
    ) {
      advanceToCloudRecovery()
    }
  }

  // If this test flakes, it likely points to a real issue. See comments in test.
  testWithTwoApps(
    "attacker can't fake an endorsement", app1Factory = { mode ->
      when (mode) {
        AppMode.Legacy -> launchLegacyWalletApp(executeWorkers = false)
        AppMode.Private -> launchNewApp(executeWorkers = false)
      }
    },
    app2Factory = { customerApp, mode ->
      when (mode) {
        AppMode.Legacy -> launchLegacyWalletApp(
          cloudBackupStore = customerApp.cloudBackupStore,
          executeWorkers = false
        )
        AppMode.Private -> launchNewApp(
          cloudBackupStore = customerApp.cloudBackupStore,
          executeWorkers = false
        )
      }
    }
  ) { customerApp, tcApp ->
    // Onboard the protected customer
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    // PC: Invite a Trusted Contact
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()

    // TC: Onboard as Lite Account and accept invite
    // TODO(W-9704): execute workers by default
    tcApp.appUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = Unit
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
    val relationshipsClient = customerApp.relationshipsF8eClientProvider.get()
    val relationships = relationshipsClient.getRelationships(
      customerAccount.accountId,
      customerAccount.config.f8eEnvironment
    ).getOrThrow()
    val unendorsedTc = relationships.unendorsedTrustedContacts
      .single()
    val relationshipsCrypto = customerApp.relationshipsCrypto
    val badKeyCert = TrustedContactKeyCertificate(
      // This is a tampered key we are trying to get into the Protected Customer backup
      delegatedDecryptionKey = relationshipsCrypto.generateDelegatedDecryptionKey()
        .getOrThrow().publicKey,
      hwAuthPublicKey = customerApp.getActiveHwAuthKey().publicKey,
      appGlobalAuthPublicKey = customerApp.getActiveAppGlobalAuthKey().publicKey,
      appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature("tampered-app-auth-key-sig"),
      trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignature("tampered-tc-identity-key-sig")
    )
    relationshipsClient.endorseTrustedContacts(
      account = customerAccount,
      endorsements = listOf(
        TrustedContactEndorsement(
          RelationshipId(unendorsedTc.relationshipId),
          badKeyCert
        )
      )
    ).getOrThrow()
    // Sanity check that the TC has been successfully endorsed with the tampered key certificate
    relationshipsClient.getRelationships(
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
      customerApp.cloudBackupDao.backup(customerAccount.accountId.serverId)
        .filterNotNull()
        .collect { backup ->
          if (backup.socRecDataAvailable) {
            fail("Trusted Contact with tampered certificate should not have been backed up")
          }
        }
    }

    // PC: Wait for the TC to end up in the tampered state.
    // Use turbineScope since we need to use testIn() when working with multiple turbines
    customerApp.appUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = Unit
    ) {
      awaitUntilBody<MoneyHomeBodyModel>()

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
      // TODO(W-9704): execute workers by default
      var customerApp = launchNewApp(executeWorkers = false)
      customerApp.onboardFullAccountWithFakeHardware(
        cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
      )
      val invite = customerApp.createTcInvite("bob")
      // TODO(W-9704): execute workers by default
      val tcApp = launchNewApp(executeWorkers = false)
      tcApp.onboardLiteAccountFromInvitation(
        invite.inviteCode,
        PROTECTED_CUSTOMER_ALIAS,
        CloudStoreAccountFake.TrustedContactFake
      )
      customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

      // TODO(W-9704): execute workers by default
      customerApp = launchNewApp(
        cloudBackupStore = customerApp.cloudBackupStore,
        hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed(),
        executeWorkers = false
      )
      customerApp.appUiStateMachine.test(
        props = Unit,
        turbineTimeout = 60.seconds
      ) {
        val cloudRecoveryForm = advanceToCloudRecovery()
        // Expect the social recovery button to exist
        cloudRecoveryForm.showSocRecButton.shouldBeTrue()
        // Do cloud recovery
        cloudRecoveryForm.onLostBitkeyClick()
        // Skip auth key rotation
        awaitUntilBody<RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice>()
          .onNotRightNow()
        awaitUntilBody<MoneyHomeBodyModel>()

        // Suspend until a cloud backup refresh happens
        customerApp.socRecCloudBackupSyncWorker.lastCheck
          .filter { it > Instant.DISTANT_PAST }
          .first()

        cancelAndIgnoreRemainingEvents()
      }

      // Expect the cloud backup to continue to contain the TC backup.
      customerApp.socRecCloudBackupSyncWorker.lastCheck.value.shouldBeGreaterThan(Instant.DISTANT_PAST)
      customerApp.readCloudBackup()
        .shouldNotBeNull()
        .socRecDataAvailable
        .shouldBeTrue()

      // PC: Start up a fresh app with clean data and start Social Challenge
      // persist cloud account stores
      // TODO(W-9704): execute workers by default
      val recoveringApp = launchNewApp(
        cloudStoreAccountRepository = customerApp.cloudStoreAccountRepository,
        cloudBackupStore = customerApp.cloudBackupStore,
        executeWorkers = false
      )
      recoveringApp.appUiStateMachine.test(
        props = Unit,
        turbineTimeout = 60.seconds
      ) {
        val cloudRecoveryForm = advanceToCloudRecovery()
        // Expect the social recovery button to exist
        cloudRecoveryForm.showSocRecButton.shouldBeTrue()
      }

      shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
    }
  }

  // TODO: This test can hang but it's still very useful
  xtest("socrec restore succeeds after cloud recovery and rotating auth keys") {
    // TODO(W-9704): execute workers by default
    var customerApp = launchNewApp(executeWorkers = false)
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
    val invite = customerApp.createTcInvite("bob")
    // TODO(W-9704): execute workers by default
    val tcApp = launchNewApp(executeWorkers = false)
    tcApp.onboardLiteAccountFromInvitation(
      inviteCode = invite.inviteCode,
      protectedCustomerName = PROTECTED_CUSTOMER_ALIAS,
      cloudStoreAccountForBackup = CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // TODO(W-9704): execute workers by default
    customerApp = launchNewApp(
      cloudBackupStore = customerApp.cloudBackupStore,
      hardwareSeed = hardwareSeed,
      executeWorkers = false
    )
    customerApp.appUiStateMachine.test(
      props = Unit,
      turbineTimeout = 60.seconds
    ) {
      advanceToCloudRecovery().onLostBitkeyClick()
      // Do auth key rotation
      screenDecideIfShouldRotate {
        clickSecondaryButton()
      }
      // Note that the auth rotation should write a new cloud backup with new socrec keys.
      awaitUntilBody<RotateAuthKeyScreens.Confirmation>()
        .onSelected()
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)
    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  testWithTwoApps(
    "Lost App Cloud Recovery should succeed after PC does Lost Hardware D&N with Trusted Contact",
    app1Factory = { mode -> launchAppForModeWithoutWorkers(mode) },
    app2Factory = { _, mode -> launchAppForModeWithoutWorkers(mode) }
  ) { customerApp, tcApp ->
    // Onboard the protected customer
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // PC: lost hardware D+N
    customerApp.fakeNfcCommands.wipeDevice()
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome()
      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)

    // PC: Start up a fresh app with clean data, persist cloud backup and hardware
    //     Perform Lost App Cloud Recovery
    val hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
    val recoveringApp = launchAppMatchingMode(
      customerApp,
      cloudStoreAccountRepository = customerApp.cloudStoreAccountRepository,
      cloudBackupStore = customerApp.cloudBackupStore,
      hardwareSeed = hardwareSeed
    )
    recoveringApp.appUiStateMachine.test(
      props = Unit,
      turbineTimeout = 60.seconds
    ) {
      advanceToCloudRecovery().onRestore()
      // Do auth key rotation
      screenDecideIfShouldRotate {
        clickSecondaryButton()
      }
      // Note that the auth rotation should write a new cloud backup with new socrec keys.
      awaitUntilBody<RotateAuthKeyScreens.Confirmation>()
        .onSelected()
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(recoveringApp)
  }

  testWithTwoApps(
    "social recovery should succeed after PC does lost app D&N",
    app1Factory = { mode -> launchAppForModeWithoutWorkers(mode) },
    app2Factory = { _, mode -> launchAppForModeWithoutWorkers(mode) }
  ) { initialCustomerApp, tcApp ->
    // Onboard the protected customer
    var customerApp = initialCustomerApp
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // PC: lost app & cloud D+N
    val hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed()
    customerApp.deleteBackupsFromFakeCloud(FullAccountIdMock)
    customerApp.fakeNfcCommands.wipeDevice()

    customerApp = launchAppMatchingMode(
      customerApp,
      hardwareSeed = hardwareSeed
    )
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostAppAndCloudRecoveryToMoneyHome(CloudStoreAccountFake.ProtectedCustomerFake)
      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)

    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  testWithTwoApps(
    "social recovery should not be possible after TC does lost app D&N",
    app1Factory = { mode -> launchAppForModeWithoutWorkers(mode) },
    app2Factory = { customerApp, mode ->
      launchAppForModeWithoutWorkers(
        mode,
        cloudBackupStore = customerApp.cloudBackupStore
      )
    }
  ) { customerApp, initialTcApp ->
    // Onboard the protected customer
    var tcApp = initialTcApp
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    tcApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake(identifier = "cloud-store-protected-customer2-fake")
    )

    // PC: Invite a Trusted Contact
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()

    // TC: accepts invite
    lateinit var relationshipId: String
    tcApp.appUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = Unit
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
    tcApp.deleteBackupsFromFakeCloud(FullAccountIdMock)
    tcApp.fakeNfcCommands.wipeDevice()

    tcApp = launchAppMatchingMode(
      tcApp,
      hardwareSeed = hardwareSeed
    )
    tcApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostAppAndCloudRecoveryToMoneyHome(CloudStoreAccountFake.ProtectedCustomerFake)
      cancelAndIgnoreRemainingEvents()
    }

    // PC: Wait for the cloud backup to be updated with no trusted contacts
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)
  }

  testWithTwoApps(
    "social recovery should succeed after PC does lost hardware D&N",
    app1Factory = { mode -> launchAppForModeWithoutWorkers(mode) },
    app2Factory = { _, mode -> launchAppForModeWithoutWorkers(mode) }
  ) { customerApp, tcApp ->
    // Onboard the protected customer
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // PC: lost hardware D+N
    customerApp.fakeNfcCommands.wipeDevice()
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome()
      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)

    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  testWithTwoApps(
    "lost hardware D&N re-verifies TC certs correctly after app kill during cloud backup",
    app1Factory = { mode -> launchAppForModeWithoutWorkers(mode) },
    app2Factory = { _, mode -> launchAppForModeWithoutWorkers(mode) }
  ) { initialCustomerApp, tcApp ->
    var customerApp = initialCustomerApp

    // 1. Setup: Onboard customer with TC
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    // 2. Wipe hardware and start Lost HW recovery, then kill app at cloud backup step
    customerApp.fakeNfcCommands.wipeDevice()
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Navigate to device settings
      awaitUntilBody<MoneyHomeBodyModel>().onSecurityHubTabClick()
      awaitUntilBody<SecurityHubBodyModel>().clickBitkeyDevice()

      // Initiate recovery
      awaitUntilBody<DeviceSettingsFormBodyModel>().onReplaceDevice()
      awaitUntilBody<HardwareReplacementInstructionsModel>().onContinue()
      awaitUntilBody<NewDeviceReadyQuestionBodyModel>().clickPrimaryButton()
      // V2 pairing flow: ActivationInstructionsV2 → NFC → fingerprint enrollment → NFC
      awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS_V2)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        .clickPrimaryButton()

      // Complete D&N delay and wait for auth rotation
      awaitUntilBody<DelayAndNotifyNewKeyReady>().onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)

      // Stop at cloud backup instructions - state is DdkBackedUp, auth keys rotated
      // TC cert regeneration has completed once, but state is not yet BackedUpToCloud
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    // 3. Kill and relaunch app - will resume from DdkBackedUp state
    // On restart, TC cert regeneration runs again using persisted originalAppGlobalAuthKey
    customerApp = customerApp.relaunchApp()

    // 4. Complete recovery
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Should resume at cloud backup (TC certs re-verified using persisted originalAppGlobalAuthKey)
      awaitUntilBody<SaveBackupInstructionsBodyModel>().onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccountFake.ProtectedCustomerFake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>().onDone()
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    // 5. Verify TC certs are correctly refreshed with new keys
    // This confirms the fix works: originalAppGlobalAuthKey was used for TC cert verification
    verifyKeyCertificatesAreRefreshed(customerApp)
  }

  testWithTwoApps(
    "lost hardware D&N should succeed after social recovery",
    app1Factory = { mode -> launchAppForModeWithoutWorkers(mode) },
    app2Factory = { _, mode -> launchAppForModeWithoutWorkers(mode) }
  ) { customerApp, tcApp ->
    // Onboard the protected customer
    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
    )
    val invite = customerApp.createTcInvite("bob")
    tcApp.onboardLiteAccountFromInvitation(
      invite.inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)

    val recoveredApp = shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)

    // PC: lost hardware D+N
    recoveredApp.fakeNfcCommands.wipeDevice()
    recoveredApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("social recovery should succeed after PC does lost hardware D&N [W3 protected customer]") {
    val customerApp = launchNewApp(executeWorkers = false)
    val tcApp = launchNewApp(executeWorkers = false)

    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake,
      hardwareType = HardwareType.W3
    )
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob", HardwareCoverageMode.W3Private)
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()
    tcApp.onboardLiteAccountFromInvitation(
      inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    val relationshipId = customerApp.awaitRelationships {
      it.invitations.any { inv -> inv.trustedContactAlias.alias == "bob" }
    }.invitations.first().relationshipId
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)

    customerApp.fakeW3NfcCommands.wipeDevice()
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(isW3 = true)
    }

    verifyKeyCertificatesAreRefreshed(customerApp)
    shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)
  }

  test("Lost App Cloud Recovery should succeed after PC does Lost Hardware D&N with TC [W3 protected customer]") {
    val customerApp = launchNewApp(executeWorkers = false)
    val tcApp = launchNewApp(executeWorkers = false)

    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake,
      hardwareType = HardwareType.W3
    )
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob", HardwareCoverageMode.W3Private)
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()
    tcApp.onboardLiteAccountFromInvitation(
      inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    val relationshipId = customerApp.awaitRelationships {
      it.invitations.any { inv -> inv.trustedContactAlias.alias == "bob" }
    }.invitations.first().relationshipId
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)

    customerApp.fakeW3NfcCommands.wipeDevice()
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(isW3 = true)
    }

    verifyKeyCertificatesAreRefreshed(customerApp)

    val recoveringApp = launchNewApp(
      cloudStoreAccountRepository = customerApp.cloudStoreAccountRepository,
      cloudBackupStore = customerApp.cloudBackupStore,
      hardwareSeed = customerApp.fakeHardwareKeyStore.getSeed(),
      w3HardwareSeed = customerApp.w3FakeHardwareKeyStore.getSeed(),
      executeWorkers = false
    )
    // The recovering app is fresh — set W3 hardware type so the W3 fake NFC
    // commands route to the W3 key store during hardware verification and
    // action proof signing.
    recoveringApp.accountConfigService.setHardwareType(HardwareType.W3)

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      turbineTimeout = 60.seconds
    ) {
      advanceToCloudRecovery().onRestore()
      // W3: confirmable NFC for hardware verification during cloud restore
      awaitW3ConfirmableNfcSession()
      screenDecideIfShouldRotate {
        clickSecondaryButton()
      }
      // W3: confirmable NFC for action proof signing during auth key rotation
      awaitW3ConfirmableNfcSession()
      awaitUntilBody<RotateAuthKeyScreens.Confirmation>()
        .onSelected()
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(recoveringApp)
  }

  test("lost HW D&N should succeed after social recovery [W3 protected customer]") {
    val customerApp = launchNewApp(executeWorkers = false)
    val tcApp = launchNewApp(
      cloudBackupStore = customerApp.cloudBackupStore,
      executeWorkers = false
    )

    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake,
      hardwareType = HardwareType.W3
    )
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob", HardwareCoverageMode.W3Private)
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()
    tcApp.onboardLiteAccountFromInvitation(
      inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    val relationshipId = customerApp.awaitRelationships {
      it.invitations.any { inv -> inv.trustedContactAlias.alias == "bob" }
    }.invitations.first().relationshipId
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)

    val recoveredApp = shouldSucceedSocialRestore(customerApp, tcApp, PROTECTED_CUSTOMER_ALIAS)

    // The recovered app was created fresh by shouldSucceedSocialRestore, so its
    // accountConfigService doesn't have W3 hardware type set (only the original
    // customerApp had it set during onboarding). Set it so that the W3 fake NFC
    // commands route to W3 behavior during the subsequent D&N flow.
    recoveredApp.accountConfigService.setHardwareType(HardwareType.W3)

    recoveredApp.fakeW3NfcCommands.wipeDevice()
    recoveredApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(isW3 = true)
    }
  }

  test("lost HW D&N re-verifies TC certs after app kill during cloud backup [W3 protected customer]") {
    var customerApp = launchNewApp(executeWorkers = false)
    val tcApp = launchNewApp(executeWorkers = false)

    customerApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake,
      hardwareType = HardwareType.W3
    )
    customerApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(customerApp)
    ) {
      advanceThroughTrustedContactInviteScreens("bob", HardwareCoverageMode.W3Private)
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = customerApp.getSharedInviteCode()
    tcApp.onboardLiteAccountFromInvitation(
      inviteCode,
      PROTECTED_CUSTOMER_ALIAS,
      CloudStoreAccountFake.TrustedContactFake
    )
    val relationshipId = customerApp.awaitRelationships {
      it.invitations.any { inv -> inv.trustedContactAlias.alias == "bob" }
    }.invitations.first().relationshipId
    customerApp.awaitTcIsVerifiedAndBackedUp(relationshipId)

    // Wipe hardware and start Lost HW recovery
    customerApp.fakeW3NfcCommands.wipeDevice()
    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      awaitUntilBody<MoneyHomeBodyModel>().onSecurityHubTabClick()
      awaitUntilBody<SecurityHubBodyModel>().clickBitkeyDevice()
      awaitUntilBody<DeviceSettingsFormBodyModel>().onReplaceDevice()
      awaitUntilBody<HardwareReplacementInstructionsModel>().onContinue()
      awaitUntilBody<NewDeviceReadyQuestionBodyModel>().clickPrimaryButton()
      // W3 pairing
      awaitUntilBody<PairNewHardwareBodyModel>(matching = { !it.primaryButton.isLoading })
        .clickPrimaryButton()
      awaitUntilBody<CompleteTwoTapBodyModel>()
        .clickPrimaryButton()

      // Complete D&N delay
      awaitUntilBody<DelayAndNotifyNewKeyReady>().onCompleteRecovery()
      // W3: confirmable NFC for spending key creation
      awaitW3ConfirmableNfcSession()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      // W3: confirmable NFC for DDK upload
      awaitW3ConfirmableNfcSession()

      // Stop at cloud backup instructions — simulate app kill
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    // Relaunch app — resumes from persisted state
    customerApp = customerApp.relaunchApp()

    customerApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      awaitUntilBody<SaveBackupInstructionsBodyModel>().onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccountFake.ProtectedCustomerFake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>().onDone()
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    verifyKeyCertificatesAreRefreshed(customerApp)
  }
})

suspend fun buildTrustedContactManagementUiStateMachineProps(
  app: AppTester,
): TrustedContactManagementScreen {
  return TrustedContactManagementScreen(
    account = app.getActiveFullAccount(),
    onExit = { fail("unexpected exit") }
  )
}

suspend fun TestScope.shouldSucceedSocialRestore(
  customerApp: AppTester,
  tcApp: AppTester,
  protectedCustomerAlias: String,
): AppTester {
  // PC: Start up a fresh app with clean data and start Social Challenge
  // persist cloud account stores
  // TODO(W-9704): execute workers by default
  val recoveringApp = launchAppMatchingMode(
    customerApp,
    cloudBackupStore = customerApp.cloudBackupStore
  )
  lateinit var challengeCode: String
  recoveringApp.appUiStateMachine.test(
    turbineTimeout = 60.seconds,
    props = Unit
  ) {
    val model =
      advanceToSocialChallengeTrustedContactList(CloudStoreAccountFake.ProtectedCustomerFake)
    challengeCode = startSocialChallenge(model)
    cancelAndIgnoreRemainingEvents()
  }

  // TC: Enter the challenge code and upload ciphertext
  tcApp.appUiStateMachine.test(
    turbineTimeout = 60.seconds,
    props = Unit
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
      is SoftwareAccount -> fail("unexpected account type")
      is OnboardingSoftwareAccount -> fail("unexpected account type")
    }
  }

  // PC: Complete Social Restore, make
  recoveringApp.appUiStateMachine.test(
    turbineTimeout = 60.seconds,
    props = Unit
  ) {
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
suspend fun verifyKeyCertificatesAreRefreshed(app: AppTester) {
  val account = app.getActiveFullAccount()
  val appPubKey = account.keybox.activeAppKeyBundle.authKey
  val hwSig = account.keybox.appGlobalAuthKeyHwSignature
  val hwPubKey = account.keybox.activeHwKeyBundle.authKey.pubKey
  hwPubKey.shouldBeEqual(app.fakeHardwareKeyStore.getAuthKeypair().publicKey.pubKey)

  val serverTcs = app.relationshipsF8eClientProvider.get()
    .getRelationships(account).getOrThrow()
    .endorsedTrustedContacts
  val dbTcs = app.relationshipsDao
    .relationships().first().getOrThrow()
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

private suspend fun TestScope.launchAppForModeWithoutWorkers(
  mode: AppMode,
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudBackupStore: CloudBackupStore? = null,
  hardwareSeed: FakeHardwareKeyStore.Seed? = null,
  w3HardwareSeed: FakeHardwareKeyStore.Seed? = null,
): AppTester =
  when (mode) {
    AppMode.Legacy -> launchLegacyWalletApp(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      hardwareSeed = hardwareSeed,
      w3HardwareSeed = w3HardwareSeed,
      executeWorkers = false
    )
    AppMode.Private -> launchNewApp(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      hardwareSeed = hardwareSeed,
      w3HardwareSeed = w3HardwareSeed,
      executeWorkers = false
    )
  }

private suspend fun TestScope.launchAppMatchingMode(
  referenceApp: AppTester,
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudBackupStore: CloudBackupStore? = null,
  hardwareSeed: FakeHardwareKeyStore.Seed? = null,
  w3HardwareSeed: FakeHardwareKeyStore.Seed? = null,
): AppTester =
  if (referenceApp.appMode == AppMode.Private) {
    launchNewApp(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      hardwareSeed = hardwareSeed,
      w3HardwareSeed = w3HardwareSeed,
      executeWorkers = false
    )
  } else {
    launchLegacyWalletApp(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      hardwareSeed = hardwareSeed,
      w3HardwareSeed = w3HardwareSeed,
      executeWorkers = false
    )
  }
