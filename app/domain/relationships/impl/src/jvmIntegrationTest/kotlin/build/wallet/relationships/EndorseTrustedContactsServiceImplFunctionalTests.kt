package build.wallet.relationships

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.signResult
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getHardwareFactorProofOfPossession
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.time.Duration
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

  suspend fun TestScope.launchAndPrepareApp() {
    app = launchNewApp(isUsingSocRecFakes = true)

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
      accountConfigService = accountConfigService
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
        hardwareProofOfPossession = app.getHardwareFactorProofOfPossession(),
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
      )
      .getOrThrow()
    // Delete the invitation since we'll be adding it back as an unendorsed trusted contact.
    relationshipsF8eClientFake.deleteInvitation(invite.invitation.relationshipId)

    // Get the PAKE code and enrollment public key that should be shared with the TC
    val pakeData = relationshipsEnrollmentAuthenticationDao
      .getByRelationshipId(invite.invitation.relationshipId)
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
      relationshipId = invite.invitation.relationshipId,
      trustedContactAlias = alias,
      sealedDelegatedDecryptionKey = tcResponse.sealedDelegatedDecryptionKey,
      enrollmentPakeKey = tcResponse.trustedContactEnrollmentPakeKey,
      enrollmentKeyConfirmation = overrideConfirmation?.encodeUtf8() ?: tcResponse.keyConfirmation,
      authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED,
      roles = setOf(TrustedContactRole.SocialRecoveryContact)
    )

    // Update unendorsed TC
    relationshipsF8eClientFake.unendorsedTrustedContacts
      .removeAll { it.relationshipId == unendorsedTc.relationshipId }
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
    val relationships = relationshipsDao.relationships().first().getOrThrow()

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
        relationshipId.shouldBe(tcBad.relationshipId)
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
