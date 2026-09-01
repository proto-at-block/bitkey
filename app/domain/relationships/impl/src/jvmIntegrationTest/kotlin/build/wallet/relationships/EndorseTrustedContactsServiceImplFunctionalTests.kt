package build.wallet.relationships

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.coroutines.createBackgroundScope
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.signResult
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getHardwareFactorProofOfPossession
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EndorseTrustedContactsServiceImplFunctionalTests : FunSpec({

  lateinit var app: AppTester
  lateinit var relationshipsService: RelationshipsServiceImpl
  lateinit var endorseTrustedContactsService: EndorseTrustedContactsServiceImpl
  lateinit var relationshipsF8eClientFake: RelationshipsF8eClientFake
  lateinit var relationshipsCrypto: RelationshipsCryptoFake
  lateinit var relationshipsDao: RelationshipsDao
  lateinit var relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao
  val accountService = AccountServiceFake().apply {
    accountState.value = Ok(ActiveAccount(FullAccountMock))
  }
  val accountConfigService = AccountConfigServiceFake()

  val alias = TrustedContactAlias("trustedContactId")

  val clock = ClockFake()

  suspend fun TestScope.launchAndPrepareApp(
    placeholderRepairRetryDelay: Duration = 10.milliseconds,
  ) {
    app = launchNewApp(
      isUsingSocRecFakes = true,
      executeWorkers = false
    )

    relationshipsF8eClientFake =
      (app.relationshipsF8eClientProvider.get() as RelationshipsF8eClientFake)
    relationshipsF8eClientFake.acceptInvitationDelay = Duration.ZERO
    relationshipsDao = app.relationshipsDao
    relationshipsEnrollmentAuthenticationDao = app.relationshipsEnrollmentAuthenticationDao
    relationshipsCrypto = app.relationshipsCryptoFake
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    accountConfigService.setActiveConfig(FullAccountMock.config)

    relationshipsService = RelationshipsServiceImpl(
      relationshipsF8eClientProvider = { relationshipsF8eClientFake },
      relationshipsDao = relationshipsDao,
      relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao,
      relationshipsCrypto = relationshipsCrypto,
      relationshipsCodeBuilder = app.relationshipsCodeBuilder,
      appSessionManager = app.appSessionManager,
      accountService = accountService,
      appCoroutineScope = app.appCoroutineScope,
      clock = clock,
      relationshipsSyncFrequency = RelationshipsSyncFrequency(1.seconds),
      accountConfigService = app.accountConfigService
    )

    endorseTrustedContactsService = EndorseTrustedContactsServiceImpl(
      relationshipsService = relationshipsService,
      relationshipsDao = relationshipsDao,
      relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao,
      relationshipsCrypto = relationshipsCrypto,
      endorseTrustedContactsF8eClientProvider = { relationshipsF8eClientFake },
      accountService = accountService,
      accountConfigService = accountConfigService,
      appSessionManager = app.appSessionManager,
      placeholderRepairRetryDelay = placeholderRepairRetryDelay
    )
  }

  suspend fun simulateAcceptedInvite(
    account: FullAccount,
    overrideConfirmation: String? = null,
    overridePakeCode: String? = null,
  ): Pair<UnendorsedTrustedContact, PublicKey<DelegatedDecryptionKey>> {
    val invite = relationshipsService
      .createInvitation(
        account = account,
        trustedContactAlias = alias,
        proof = PrivilegedActionProof.HwKeyProof(app.getHardwareFactorProofOfPossession()),
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
    )
      .getOrThrow { it.cause }
    // Delete the invitation since we'll be adding it back as an unendorsed trusted contact.
    relationshipsF8eClientFake.deleteInvitation(invite.invitation.id.value)

    // Get the PAKE code and enrollment public key that should be shared with the TC
    val pakeData = relationshipsEnrollmentAuthenticationDao
      .getByRelationshipId(invite.invitation.id.value)
      .getOrThrow()
      .shouldNotBeNull()
    val delegatedDecryptionKey = relationshipsCrypto.generateDelegatedDecryptionKey().getOrThrow()

    // Simulate the TC accepting the invitation and sending their identity key
    val pakeCode = if (overridePakeCode != null) {
      PakeCode(overridePakeCode.toByteArray().toByteString())
    } else {
      pakeData.pakeCode
    }
    val tcResponse = relationshipsCrypto
      .encryptDelegatedDecryptionKey(
        password = pakeCode,
        protectedCustomerEnrollmentPakeKey = pakeData.protectedCustomerEnrollmentPakeKey.publicKey,
        delegatedDecryptionKey = delegatedDecryptionKey.publicKey
      )
      .getOrThrow()
    val unendorsedTc = UnendorsedTrustedContact(
      id = invite.invitation.id,
      trustedContactAlias = alias,
      sealedDelegatedDecryptionKey = tcResponse.sealedDelegatedDecryptionKey,
      enrollmentPakeKey = tcResponse.trustedContactEnrollmentPakeKey,
      enrollmentKeyConfirmation = overrideConfirmation?.encodeUtf8() ?: tcResponse.keyConfirmation,
      authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED,
      roles = setOf(TrustedContactRole.SocialRecoveryContact)
    )

    // Update unendorsed TC
    relationshipsF8eClientFake.unendorsedTrustedContacts
      .removeAll { it.id == unendorsedTc.id }
    relationshipsF8eClientFake.unendorsedTrustedContacts.add(unendorsedTc)

    relationshipsService.syncAndVerifyRelationships(account).getOrThrow()
    return Pair(unendorsedTc, delegatedDecryptionKey.publicKey)
  }

  test("happy path") {
    launchAndPrepareApp()

    // Onboard new account
    val account = app.onboardFullAccountWithFakeHardware()

    // Create TC invite
    val (_, tcIdentityKey) = simulateAcceptedInvite(account)

    // PC to authenticate and verify unendorsed TCs
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    // Verify the key certificate
    val keyCertificate = relationshipsF8eClientFake.keyCertificates.single()
    relationshipsCrypto.verifyKeyCertificate(account, keyCertificate)
      .shouldBeOk()
      // Verify the TC's identity key
      .shouldBe(tcIdentityKey)

    // Fetch relationships
    val relationships = relationshipsService.syncAndVerifyRelationships(account).getOrThrow()

    // TC should be completely endorsed
    relationships
      .endorsedTrustedContacts
      .single()
      .run {
        trustedContactAlias.shouldBe(alias)
        authenticationState.shouldBe(TrustedContactAuthenticationState.VERIFIED)
      }

    relationships.unendorsedTrustedContacts.shouldBeEmpty()
    relationships.invitations.shouldBeEmpty()
    relationships.protectedCustomers.shouldBeEmpty()
  }

  test("worker does not poll when placeholder repair is not pending") {
    launchAndPrepareApp(placeholderRepairRetryDelay = 100.milliseconds)
    val account = app.onboardFullAccountWithFakeHardware()
    accountService.setActiveAccount(account)

    val callsBeforeWorker = relationshipsF8eClientFake.getRelationshipsCallCount
    createBackgroundScope().launch {
      endorseTrustedContactsService.executeWork()
    }
    withTimeout(5.seconds) {
      while (relationshipsF8eClientFake.getRelationshipsCallCount == callsBeforeWorker) {
        delay(10.milliseconds)
      }
    }
    delay(300.milliseconds)
    val callsAfterInitialSync = relationshipsF8eClientFake.getRelationshipsCallCount

    delay(350.milliseconds)

    relationshipsF8eClientFake.getRelationshipsCallCount.shouldBe(callsAfterInitialSync)
  }

  test("worker retries a transient unendorsed contact failure") {
    launchAndPrepareApp(placeholderRepairRetryDelay = 10.milliseconds)
    val account = app.onboardFullAccountWithFakeHardware()
    simulateAcceptedInvite(account)
    accountService.setActiveAccount(account)
    relationshipsF8eClientFake.endorseTrustedContactsFailuresRemaining = 1

    createBackgroundScope().launch {
      endorseTrustedContactsService.executeWork()
    }

    withTimeout(5.seconds) {
      while (
        relationshipsF8eClientFake.unendorsedTrustedContacts.isNotEmpty() ||
        relationshipsF8eClientFake.endorsedTrustedContacts.isEmpty()
      ) {
        delay(10.milliseconds)
      }
    }
  }

  test("worker preserves terminal unendorsed authentication state from the DAO") {
    launchAndPrepareApp()
    val account = app.onboardFullAccountWithFakeHardware()
    val (contact, _) = simulateAcceptedInvite(account)
    relationshipsService.syncAndVerifyRelationships(account).getOrThrow()
    relationshipsDao.setUnendorsedTrustedContactAuthenticationState(
      recoveryRelationshipId = contact.id.value,
      authenticationState = TrustedContactAuthenticationState.FAILED
    ).getOrThrow()
    accountService.setActiveAccount(account)

    createBackgroundScope().launch {
      endorseTrustedContactsService.executeWork()
    }

    delay(200.milliseconds)

    relationshipsF8eClientFake.unendorsedTrustedContacts.single().id.shouldBe(contact.id)
    relationshipsF8eClientFake.endorsedTrustedContacts.shouldBeEmpty()
    relationshipsDao.relationships().first().getOrThrow()
      .unendorsedTrustedContacts.single().authenticationState
      .shouldBe(TrustedContactAuthenticationState.FAILED)
  }

  test("worker pauses retries while the app is backgrounded") {
    launchAndPrepareApp(placeholderRepairRetryDelay = 50.milliseconds)
    val account = app.onboardFullAccountWithFakeHardware()
    simulateAcceptedInvite(account)
    accountService.setActiveAccount(account)
    relationshipsF8eClientFake.endorseTrustedContactsFailuresRemaining = 2

    createBackgroundScope().launch {
      endorseTrustedContactsService.executeWork()
    }

    withTimeout(5.seconds) {
      while (relationshipsF8eClientFake.endorseTrustedContactsFailuresRemaining == 2) {
        delay(10.milliseconds)
      }
    }
    app.appSessionManager.appDidEnterBackground()
    val callsAfterInitialAttempt = relationshipsF8eClientFake.getRelationshipsCallCount

    delay(200.milliseconds)
    relationshipsF8eClientFake.getRelationshipsCallCount.shouldBe(callsAfterInitialAttempt)

    app.appSessionManager.appDidEnterForeground()
    withTimeout(5.seconds) {
      while (relationshipsF8eClientFake.endorseTrustedContactsFailuresRemaining != 0) {
        delay(10.milliseconds)
      }
    }
  }

  test("Authenticate/regenerate/endorse - Empty") {
    launchAndPrepareApp()

    // Onboard new account
    val account = app.onboardFullAccountWithFakeHardware()

    // Generate new Certs
    val newAppKey = relationshipsCrypto.generateAppAuthKeypair()
    val newHwKey = app.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = app.messageSigner.signResult(
      newAppKey.publicKey.value.encodeUtf8(),
      newHwKey.privateKey
    ).getOrThrow()

    // Verify test setup
    relationshipsF8eClientFake.endorsedTrustedContacts.shouldBeEmpty()

    val result = endorseTrustedContactsService.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      contacts = relationshipsF8eClientFake.endorsedTrustedContacts,
      oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
      oldHwAuthKey = account.keybox.activeHwKeyBundle.authKey,
      newAppGlobalAuthKey = newAppKey.publicKey,
      newAppGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(hwSignature)
    )

    result.shouldBeOk()
  }

  test("Authenticate/regenerate/endorse - Success") {
    launchAndPrepareApp()

    // Onboard new account
    val account = app.onboardFullAccountWithFakeHardware()

    // Create TC invite
    simulateAcceptedInvite(account)

    // Endorse
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    // Generate new Certs
    val newAppKey = relationshipsCrypto.generateAppAuthKeypair()
    val newHwKey = app.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = app.messageSigner.signResult(
      newAppKey.publicKey.value.encodeUtf8(),
      newHwKey.privateKey
    ).getOrThrow()

    // Verify test setup
    relationshipsF8eClientFake.endorsedTrustedContacts.shouldNotBeEmpty()

    val result = endorseTrustedContactsService.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      contacts = relationshipsF8eClientFake.endorsedTrustedContacts,
      oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
      oldHwAuthKey = account.keybox.activeHwKeyBundle.authKey,
      newAppGlobalAuthKey = newAppKey.publicKey,
      newAppGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(hwSignature)
    )

    result.shouldBeOk()
  }

  test("Authenticate/regenerate/endorse - Tamper") {
    launchAndPrepareApp()

    // Onboard new account
    val account = app.onboardFullAccountWithFakeHardware()

    // Create TC invite
    simulateAcceptedInvite(account)

    // Endorse
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    // Generate New Certs
    val newAppKey = relationshipsCrypto.generateAppAuthKeypair()
    val newHwKey = app.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = app.messageSigner.signResult(
      newAppKey.publicKey.value.encodeUtf8(),
      newHwKey.privateKey
    ).getOrThrow()

    // Verify test setup
    relationshipsF8eClientFake.endorsedTrustedContacts.shouldNotBeEmpty()

    val result = endorseTrustedContactsService.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      contacts = listOf(
        relationshipsF8eClientFake.endorsedTrustedContacts.single().copy(
          keyCertificate = TrustedContactKeyCertificateFake2
        )
      ),
      oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
      oldHwAuthKey = account.keybox.activeHwKeyBundle.authKey,
      newAppGlobalAuthKey = newAppKey.publicKey,
      newAppGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(hwSignature)
    )
    val relationships = relationshipsDao.relationships().first().getOrThrow()

    relationships.endorsedTrustedContacts.single().authenticationState.shouldBe(
      TrustedContactAuthenticationState.TAMPERED
    )
    result.shouldBeOk()
  }

  test("placeholder repair retries after a transient upload failure") {
    launchAndPrepareApp()

    val account = app.onboardFullAccountWithFakeHardware()

    val (_, tcIdentityKey) = simulateAcceptedInvite(account)
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    // Replace the hardware endorsement with the W3 placeholder.
    val endorsed = relationshipsF8eClientFake.endorsedTrustedContacts.single()
    val placeholderCertificate = endorsed.keyCertificate.copy(
      appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature(
        AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
      )
    )
    relationshipsF8eClientFake.endorsedTrustedContacts.clear()
    relationshipsF8eClientFake.endorsedTrustedContacts.add(
      endorsed.copy(keyCertificate = placeholderCertificate)
    )
    relationshipsF8eClientFake.keyCertificates.clear()
    relationshipsF8eClientFake.keyCertificates.add(placeholderCertificate)

    val placeholderContact = relationshipsService.syncAndVerifyRelationships(account)
      .getOrThrow()
      .endorsedTrustedContacts
      .single()
    placeholderContact.needsHwVerification.shouldBe(true)

    val placeholderAccount = account.copy(
      keybox = account.keybox.copy(
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
          AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
        )
      )
    )
    accountService.setActiveAccount(placeholderAccount)
    relationshipsF8eClientFake.endorseTrustedContactsFailuresRemaining = 1
    createBackgroundScope().launch {
      endorseTrustedContactsService.executeWork()
    }

    relationshipsF8eClientFake.getRelationshipsFailuresRemaining = 1
    accountService.setActiveAccount(account)

    relationshipsDao.relationships()
      .first { result ->
        result.getOrThrow().endorsedTrustedContacts.singleOrNull()
          ?.authenticationState == TrustedContactAuthenticationState.VERIFIED
      }

    // Repair preserves the contact identity key.
    val repairedCertificate = relationshipsF8eClientFake.keyCertificates.last()
    repairedCertificate.appAuthGlobalKeyHwSignature
      .shouldBe(account.keybox.appGlobalAuthKeyHwSignature)
    repairedCertificate.delegatedDecryptionKey.shouldBe(tcIdentityKey)

    // Service and persisted state return to verified.
    relationshipsService.syncAndVerifyRelationships(account).getOrThrow()
      .endorsedTrustedContacts
      .single()
      .run {
        authenticationState.shouldBe(TrustedContactAuthenticationState.VERIFIED)
      }
    relationshipsDao.relationships()
      .first { result ->
        result.getOrThrow().endorsedTrustedContacts.singleOrNull()?.let {
          it.authenticationState == TrustedContactAuthenticationState.VERIFIED &&
            !it.keyCertificate.appAuthGlobalKeyHwSignature.isPlaceholder
        } == true
      }
      .getOrThrow()
      .endorsedTrustedContacts
      .single()
      .run {
        authenticationState.shouldBe(TrustedContactAuthenticationState.VERIFIED)
      }
  }

  test("pending placeholder repair does not block newer relationships") {
    launchAndPrepareApp(placeholderRepairRetryDelay = 100.milliseconds)

    val account = app.onboardFullAccountWithFakeHardware()
    simulateAcceptedInvite(account)
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    val endorsed = relationshipsF8eClientFake.endorsedTrustedContacts.single()
    val placeholderCertificate = endorsed.keyCertificate.copy(
      appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature(
        AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
      )
    )
    relationshipsF8eClientFake.endorsedTrustedContacts.clear()
    relationshipsF8eClientFake.endorsedTrustedContacts.add(
      endorsed.copy(keyCertificate = placeholderCertificate)
    )
    relationshipsService.syncAndVerifyRelationships(account).getOrThrow()

    accountService.setActiveAccount(account)
    val callsBeforeRepair = relationshipsF8eClientFake.endorseTrustedContactsCallCount
    relationshipsF8eClientFake.failReendorsements = true
    createBackgroundScope().launch {
      endorseTrustedContactsService.executeWork()
    }
    withTimeout(5.seconds) {
      while (relationshipsF8eClientFake.endorseTrustedContactsCallCount == callsBeforeRepair) {
        delay(10.milliseconds)
      }
    }

    // Keep the worker from racing the test-only deterministic crypto fake while constructing the
    // newly accepted relationship, then let the queued relationship change resume processing.
    app.appSessionManager.appDidEnterBackground()
    simulateAcceptedInvite(account)
    app.appSessionManager.appDidEnterForeground()

    val completed = withTimeoutOrNull(5.seconds) {
      while (
        relationshipsF8eClientFake.unendorsedTrustedContacts.isNotEmpty() ||
        relationshipsF8eClientFake.endorsedTrustedContacts.size != 2
      ) {
        delay(10.milliseconds)
      }
      true
    }
    check(completed == true) {
      "Repair did not process latest snapshot: " +
        "unendorsed=${relationshipsF8eClientFake.unendorsedTrustedContacts.size}, " +
        "endorsed=${relationshipsF8eClientFake.endorsedTrustedContacts.size}, " +
        "endorseCalls=${relationshipsF8eClientFake.endorseTrustedContactsCallCount}"
    }
    relationshipsF8eClientFake.unendorsedTrustedContacts.shouldBeEmpty()
    relationshipsF8eClientFake.endorsedTrustedContacts.size.shouldBe(2)
    relationshipsF8eClientFake.endorsedTrustedContacts
      .any { it.keyCertificate.appAuthGlobalKeyHwSignature.isPlaceholder }
      .shouldBe(true)
  }

  test("repairPlaceholderEndorsements no-ops while the keybox still holds a placeholder") {
    launchAndPrepareApp()

    val account = app.onboardFullAccountWithFakeHardware()

    simulateAcceptedInvite(account)
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )
    val endorsed = relationshipsF8eClientFake.endorsedTrustedContacts.single()
    val placeholderContact = endorsed.copy(
      keyCertificate = endorsed.keyCertificate.copy(
        appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature(
          AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
        )
      )
    )
    val certificatesBefore = relationshipsF8eClientFake.keyCertificates.toList()

    // Repair waits until the keybox has a real signature.
    val placeholderKeyboxAccount = account.copy(
      keybox = account.keybox.copy(
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
          AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
        )
      )
    )

    endorseTrustedContactsService.repairPlaceholderEndorsements(
      contacts = listOf(placeholderContact),
      account = placeholderKeyboxAccount
    ).shouldBeOk()

    relationshipsF8eClientFake.keyCertificates.shouldBe(certificatesBefore)
  }

  test("missing pake data") {
    launchAndPrepareApp()

    // Onboard new account
    val account = app.onboardFullAccountWithFakeHardware()

    // Creat TC invite
    simulateAcceptedInvite(account)

    // Clear the PAKE data
    relationshipsEnrollmentAuthenticationDao.clear().getOrThrow()

    // Attempt to authenticate and verify unendorsed TCs
    endorseTrustedContactsService
      .authenticateAndEndorse(relationshipsF8eClientFake.unendorsedTrustedContacts, account)

    // Fetch relationships
    val relationships = relationshipsDao.relationships().first().getOrThrow()

    relationships.endorsedTrustedContacts.shouldBeEmpty()

    // Verify that the unendorsed TC is in a failed state
    relationships
      .unendorsedTrustedContacts
      .single()
      .authenticationState
      .shouldBe(TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE)
  }

  test("authentication failed due to invalid key confirmation") {
    launchAndPrepareApp()

    val account = app.onboardFullAccountWithFakeHardware()

    simulateAcceptedInvite(account, overrideConfirmation = "badConfirmation")

    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    relationshipsDao.relationships().first().getOrThrow()
      .unendorsedTrustedContacts
      .single()
      .authenticationState
      .shouldBe(TrustedContactAuthenticationState.FAILED)
  }

  test("authentication failed due to wrong pake password") {
    launchAndPrepareApp()

    val account = app.onboardFullAccountWithFakeHardware()

    simulateAcceptedInvite(account, overridePakeCode = "F00DBAD")

    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    relationshipsDao.relationships().first().getOrThrow()
      .unendorsedTrustedContacts
      .single()
      .authenticationState
      .shouldBe(TrustedContactAuthenticationState.FAILED)
  }

  test("one bad contact does not block a good contact") {
    launchAndPrepareApp()

    // Onboard new account
    val account = app.onboardFullAccountWithFakeHardware()
    val (tcBad, _) = simulateAcceptedInvite(account, overrideConfirmation = "badConfirmation")
    val (tcGood, tcGoodIdentityKey) = simulateAcceptedInvite(account)

    // PC to authenticate and verify unendorsed TCs
    endorseTrustedContactsService
      .authenticateAndEndorse(relationshipsF8eClientFake.unendorsedTrustedContacts, account)

    // Fetch relationships
    val relationships = relationshipsDao.relationships().first().getOrThrow()

    // Verify that the unendorsed TC is in a failed state
    relationships
      .unendorsedTrustedContacts
      .single()
      .run {
        id.shouldBe(tcBad.id)
        authenticationState.shouldBe(TrustedContactAuthenticationState.FAILED)
      }

    // Verify that the unendorsed TC is in the endorsed state
    relationships
      .endorsedTrustedContacts
      .single()
      .run {
        identityKey.shouldBe(tcGoodIdentityKey)
        trustedContactAlias.shouldBe(tcGood.trustedContactAlias)
        authenticationState.shouldBe(TrustedContactAuthenticationState.VERIFIED)
      }

    // Verify the key certificate
    relationshipsF8eClientFake.keyCertificates
      .single()
      .run {
        delegatedDecryptionKey.shouldBe(tcGoodIdentityKey)

        relationshipsCrypto.verifyKeyCertificate(keyCertificate = this, account = account)
      }
  }
})
