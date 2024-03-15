package build.wallet.recovery.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake2
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.encrypt.signResult
import build.wallet.f8e.socrec.SocialRecoveryServiceFake
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import build.wallet.testing.shouldBeOk
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

class TrustedContactKeyAuthenticatorImplComponentTests : FunSpec({
  lateinit var appTester: AppTester
  lateinit var socRecRepository: SocRecRelationshipsRepository
  lateinit var socialRecoveryService: SocialRecoveryServiceFake
  lateinit var trustedContactKeyAuthenticator: TrustedContactKeyAuthenticatorImpl
  lateinit var socRecCrypto: SocRecCryptoFake
  lateinit var socRecRelationshipsDao: SocRecRelationshipsDao
  lateinit var socRecEnrollmentAuthenticationDao: SocRecEnrollmentAuthenticationDao

  val alias = TrustedContactAlias("trustedContactId")

  beforeTest {
    appTester = launchNewApp()

    socialRecoveryService = appTester.app.socialRecoveryServiceFake.apply {
      acceptInvitationDelay = Duration.ZERO
    }
    socRecRelationshipsDao = appTester.app.socRecRelationshipsDao
    socRecEnrollmentAuthenticationDao = appTester.app.socRecEnrollmentAuthenticationDao
    socRecCrypto = appTester.app.socRecCryptoFake
    socRecRepository = SocRecRelationshipsRepositoryImpl(
      { socialRecoveryService },
      appTester.app.socRecRelationshipsDao,
      socRecEnrollmentAuthenticationDao,
      socRecCrypto,
      appTester.app.pakeCodeBuilder
    )

    trustedContactKeyAuthenticator = TrustedContactKeyAuthenticatorImpl(
      socRecRepository,
      socRecRelationshipsDao,
      socRecEnrollmentAuthenticationDao,
      socRecCrypto,
      suspend { socialRecoveryService }
    )
  }

  suspend fun simulateAcceptedInvite(
    account: FullAccount,
    overrideConfirmation: String? = null,
    overridePakeCode: String? = null,
  ): Pair<UnendorsedTrustedContact, DelegatedDecryptionKey> {
    val invite = socRecRepository
      .createInvitation(
        account = account,
        trustedContactAlias = alias,
        hardwareProofOfPossession = appTester.getHardwareFactorProofOfPossession(account.keybox)
      )
      .getOrThrow()
    // Delete the invitation since we'll be adding it back as an unendorsed trusted contact.
    socialRecoveryService.deleteInvitation(invite.invitation.recoveryRelationshipId)

    // Get the PAKE code and enrollment public key that should be shared with the TC
    val pakeData = socRecEnrollmentAuthenticationDao
      .getByRelationshipId(invite.invitation.recoveryRelationshipId)
      .getOrThrow()
      .shouldNotBeNull()
    val delegatedDecryptionKey = socRecCrypto.generateDelegatedDecryptionKey().getOrThrow()

    // Simulate the TC accepting the invitation and sending their identity key
    val pakeCode = if (overridePakeCode != null) {
      PakeCode(overridePakeCode.toByteArray().toByteString())
    } else {
      PakeCode(pakeData.pakeCode)
    }
    val tcResponse = socRecCrypto
      .encryptDelegatedDecryptionKey(
        password = pakeCode,
        protectedCustomerEnrollmentPakeKey = pakeData.protectedCustomerEnrollmentPakeKey,
        delegatedDecryptionKey = delegatedDecryptionKey
      )
      .getOrThrow()
    val unendorsedTc = UnendorsedTrustedContact(
      recoveryRelationshipId = invite.invitation.recoveryRelationshipId,
      trustedContactAlias = alias,
      sealedDelegatedDecryptionKey = tcResponse.sealedDelegatedDecryptionKey,
      enrollmentPakeKey = tcResponse.trustedContactEnrollmentPakeKey,
      enrollmentKeyConfirmation = overrideConfirmation?.encodeUtf8() ?: tcResponse.keyConfirmation,
      authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED
    )

    // Update unendorsed TC
    socialRecoveryService.unendorsedTrustedContacts
      .removeAll { it.recoveryRelationshipId == unendorsedTc.recoveryRelationshipId }
    socialRecoveryService.unendorsedTrustedContacts.add(unendorsedTc)

    socRecRepository.syncAndVerifyRelationships(account).getOrThrow()
    return Pair(unendorsedTc, delegatedDecryptionKey)
  }

  test("happy path") {
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()

    // Creat TC invite
    val (_, tcIdentityKey) = simulateAcceptedInvite(account)

    // PC to authenticate and verify unendorsed TCs
    trustedContactKeyAuthenticator.authenticateAndEndorse(
      socialRecoveryService.unendorsedTrustedContacts,
      account
    )

    // Verify the key certificate
    val keyCertificate = socialRecoveryService.keyCertificates.single()
    socRecCrypto.verifyKeyCertificate(account, keyCertificate)
      .shouldBeOk().run {
        // Verify the TC's identity key
        publicKey.shouldBe(tcIdentityKey.publicKey)
      }

    // Fetch relationships
    val relationships = socRecRelationshipsDao.socRecRelationships().first().getOrThrow()

    // TC should be completely endorsed
    relationships
      .trustedContacts
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
    val newAppKey = socRecCrypto.generateAppAuthKeypair()
    val newHwKey = appTester.app.appComponent.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = appTester.app.appComponent.messageSigner.signResult(newAppKey.publicKey.pubKey.value.encodeUtf8(), newHwKey.privateKey).getOrThrow()

    // Verify test setup
    socialRecoveryService.trustedContacts.shouldBeEmpty()

    val result = trustedContactKeyAuthenticator.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
      contacts = socialRecoveryService.trustedContacts,
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
    trustedContactKeyAuthenticator.authenticateAndEndorse(
      socialRecoveryService.unendorsedTrustedContacts,
      account
    )

    // Generate new Certs
    val newAppKey = socRecCrypto.generateAppAuthKeypair()
    val newHwKey = appTester.app.appComponent.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = appTester.app.appComponent.messageSigner.signResult(newAppKey.publicKey.pubKey.value.encodeUtf8(), newHwKey.privateKey).getOrThrow()

    // Verify test setup
    socialRecoveryService.trustedContacts.shouldNotBeEmpty()

    val result = trustedContactKeyAuthenticator.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
      contacts = socialRecoveryService.trustedContacts,
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
    trustedContactKeyAuthenticator.authenticateAndEndorse(
      socialRecoveryService.unendorsedTrustedContacts,
      account
    )

    // Generate New Certs
    val newAppKey = socRecCrypto.generateAppAuthKeypair()
    val newHwKey = appTester.app.appComponent.secp256k1KeyGenerator.generateKeypair()
    val hwSignature = appTester.app.appComponent.messageSigner.signResult(newAppKey.publicKey.pubKey.value.encodeUtf8(), newHwKey.privateKey).getOrThrow()

    // Verify test setup
    socialRecoveryService.trustedContacts.shouldNotBeEmpty()

    val result = trustedContactKeyAuthenticator.authenticateRegenerateAndEndorse(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
      contacts = listOf(
        socialRecoveryService.trustedContacts.single().copy(
          keyCertificate = TrustedContactKeyCertificateFake2
        )
      ),
      oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
      oldHwAuthKey = account.keybox.activeHwKeyBundle.authKey,
      newAppGlobalAuthKey = newAppKey.publicKey,
      newAppGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(hwSignature)
    )
    val relationships = socRecRelationshipsDao.socRecRelationships().first().getOrThrow()

    relationships.trustedContacts.single().authenticationState.shouldBe(TrustedContactAuthenticationState.TAMPERED)
    result.shouldBeOk()
  }

  test("missing pake data") {
    // Onboard new account
    val account = appTester.onboardFullAccountWithFakeHardware()

    // Creat TC invite
    simulateAcceptedInvite(account)

    // Clear the PAKE data
    socRecEnrollmentAuthenticationDao.clear().getOrThrow()

    // Attempt to authenticate and verify unendorsed TCs
    trustedContactKeyAuthenticator
      .authenticateAndEndorse(socialRecoveryService.unendorsedTrustedContacts, account)

    // Fetch relationships
    val relationships = socRecRelationshipsDao.socRecRelationships().first().getOrThrow()

    relationships.trustedContacts.shouldBeEmpty()

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

    trustedContactKeyAuthenticator.authenticateAndEndorse(
      socialRecoveryService.unendorsedTrustedContacts,
      account
    )

    socRecRelationshipsDao.socRecRelationships().first().getOrThrow()
      .unendorsedTrustedContacts
      .single()
      .authenticationState
      .shouldBe(TrustedContactAuthenticationState.FAILED)
  }

  test("authentication failed due to wrong pake password") {
    val account = appTester.onboardFullAccountWithFakeHardware()

    simulateAcceptedInvite(account, overridePakeCode = "F00DBAD")

    trustedContactKeyAuthenticator.authenticateAndEndorse(
      socialRecoveryService.unendorsedTrustedContacts,
      account
    )

    socRecRelationshipsDao.socRecRelationships().first().getOrThrow()
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
    trustedContactKeyAuthenticator
      .authenticateAndEndorse(socialRecoveryService.unendorsedTrustedContacts, account)

    // Fetch relationships
    val relationships = socRecRelationshipsDao.socRecRelationships().first().getOrThrow()

    // Verify that the unendorsed TC is in a failed state
    relationships
      .unendorsedTrustedContacts
      .single()
      .run {
        recoveryRelationshipId.shouldBe(tcBad.recoveryRelationshipId)
        authenticationState.shouldBe(TrustedContactAuthenticationState.FAILED)
      }

    // Verify that the unendorsed TC is in the endorsed state
    relationships
      .trustedContacts
      .single()
      .run {
        identityKey.publicKey.shouldBe(tcGoodIdentityKey.publicKey)
        trustedContactAlias.shouldBe(tcGood.trustedContactAlias)
        authenticationState.shouldBe(TrustedContactAuthenticationState.VERIFIED)
      }

    // Verify the key certificate
    socialRecoveryService.keyCertificates
      .single()
      .run {
        delegatedDecryptionKey.publicKey.shouldBe(tcGoodIdentityKey.publicKey)

        socRecCrypto.verifyKeyCertificate(keyCertificate = this, account = account)
      }
  }
})
