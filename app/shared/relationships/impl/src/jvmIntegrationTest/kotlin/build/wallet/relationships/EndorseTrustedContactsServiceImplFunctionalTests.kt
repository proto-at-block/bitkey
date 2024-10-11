package build.wallet.relationships

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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.time.Duration

class EndorseTrustedContactsServiceImplFunctionalTests : FunSpec({

  coroutineTestScope = true

  lateinit var appTester: AppTester
  lateinit var relationshipsService: RelationshipsServiceImpl
  lateinit var endorseTrustedContactsService: EndorseTrustedContactsServiceImpl
  lateinit var relationshipsF8eClientFake: RelationshipsF8eClientFake
  lateinit var relationshipsCrypto: RelationshipsCryptoFake
  lateinit var relationshipsDao: RelationshipsDao
  lateinit var relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao
  val accountService = AccountServiceFake().apply {
    accountState.value = Ok(ActiveAccount(FullAccountMock))
  }

  val alias = TrustedContactAlias("trustedContactId")

  beforeTest {
    appTester = launchNewApp(isUsingSocRecFakes = true)

    relationshipsF8eClientFake =
      (appTester.app.appComponent.relationshipsF8eClientProvider.get() as RelationshipsF8eClientFake)
    relationshipsF8eClientFake.acceptInvitationDelay = Duration.ZERO
    relationshipsDao = appTester.app.appComponent.relationshipsDao
    relationshipsEnrollmentAuthenticationDao = appTester.app.appComponent.relationshipsEnrollmentAuthenticationDao
    relationshipsCrypto = appTester.app.relationshipsCryptoFake
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)

    relationshipsService = RelationshipsServiceImpl(
      relationshipsF8eClientProvider = { relationshipsF8eClientFake },
      relationshipsDao = relationshipsDao,
      relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao,
      relationshipsCrypto = relationshipsCrypto,
      relationshipsCodeBuilder = appTester.app.appComponent.relationshipsCodeBuilder,
      appSessionManager = appTester.app.appComponent.appSessionManager,
      accountService = accountService
    )

    endorseTrustedContactsService = EndorseTrustedContactsServiceImpl(
      relationshipsService = relationshipsService,
      relationshipsDao = relationshipsDao,
      relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao,
      relationshipsCrypto = relationshipsCrypto,
      endorseTrustedContactsF8eClientProvider = suspend { relationshipsF8eClientFake },
      accountService = accountService
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
        hardwareProofOfPossession = appTester.getHardwareFactorProofOfPossession(),
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
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()

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
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()

    // Generate new Certs
    val newAppKey = relationshipsCrypto.generateAppAuthKeypair()
    val newHwKey = appTester.app.appComponent.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = appTester.app.appComponent.messageSigner.signResult(
      newAppKey.publicKey.value.encodeUtf8(),
      newHwKey.privateKey
    ).getOrThrow()

    // Verify test setup
    relationshipsF8eClientFake.endorsedTrustedContacts.shouldBeEmpty()

    val result = endorseTrustedContactsService.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
      contacts = relationshipsF8eClientFake.endorsedTrustedContacts,
      oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
      oldHwAuthKey = account.keybox.activeHwKeyBundle.authKey,
      newAppGlobalAuthKey = newAppKey.publicKey,
      newAppGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(hwSignature)
    )

    result.shouldBeOk()
  }

  test("Authenticate/regenerate/endorse - Success") {
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()

    // Create TC invite
    simulateAcceptedInvite(account)

    // Endorse
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    // Generate new Certs
    val newAppKey = relationshipsCrypto.generateAppAuthKeypair()
    val newHwKey = appTester.app.appComponent.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = appTester.app.appComponent.messageSigner.signResult(
      newAppKey.publicKey.value.encodeUtf8(),
      newHwKey.privateKey
    ).getOrThrow()

    // Verify test setup
    relationshipsF8eClientFake.endorsedTrustedContacts.shouldNotBeEmpty()

    val result = endorseTrustedContactsService.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
      contacts = relationshipsF8eClientFake.endorsedTrustedContacts,
      oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
      oldHwAuthKey = account.keybox.activeHwKeyBundle.authKey,
      newAppGlobalAuthKey = newAppKey.publicKey,
      newAppGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(hwSignature)
    )

    result.shouldBeOk()
  }

  test("Authenticate/regenerate/endorse - Tamper") {
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()

    // Create TC invite
    simulateAcceptedInvite(account)

    // Endorse
    endorseTrustedContactsService.authenticateAndEndorse(
      relationshipsF8eClientFake.unendorsedTrustedContacts,
      account
    )

    // Generate New Certs
    val newAppKey = relationshipsCrypto.generateAppAuthKeypair()
    val newHwKey = appTester.app.appComponent.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = appTester.app.appComponent.messageSigner.signResult(
      newAppKey.publicKey.value.encodeUtf8(),
      newHwKey.privateKey
    ).getOrThrow()

    // Verify test setup
    relationshipsF8eClientFake.endorsedTrustedContacts.shouldNotBeEmpty()

    val result = endorseTrustedContactsService.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
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
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()

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
    val account = appTester.onboardFullAccountWithFakeHardware()

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
    val account = appTester.onboardFullAccountWithFakeHardware()

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
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()
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
