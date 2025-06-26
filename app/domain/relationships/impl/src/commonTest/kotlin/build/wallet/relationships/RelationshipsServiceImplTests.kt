@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.relationships

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import bitkey.relationships.Relationships
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.f8e.relationships.isEmpty
import build.wallet.f8e.relationships.shouldBeEmpty
import build.wallet.f8e.relationships.shouldOnlyHaveEndorsed
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.sqldelight.InMemorySqlDriverFactory
import build.wallet.testing.shouldBeErrOfType
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class RelationshipsServiceImplTests : FunSpec({
  val databaseProvider = BitkeyDatabaseProviderImpl(
    InMemorySqlDriverFactory()
  )
  val dao = RelationshipsDaoImpl(databaseProvider)
  val appKeyDao = AppPrivateKeyDaoFake()
  val authDao = RelationshipsEnrollmentAuthenticationDaoImpl(appKeyDao, databaseProvider)

  lateinit var relationshipsF8eFake: RelationshipsF8eClientFake

  val relationshipsCrypto = RelationshipsCryptoFake()

  val tcAliceUnverified = EndorsedTrustedContact(
    relationshipId = "rel-123",
    trustedContactAlias = TrustedContactAlias("alice"),
    authenticationState = AWAITING_VERIFY,
    keyCertificate = TrustedContactKeyCertificateFake,
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )
  val tcAliceVerified = tcAliceUnverified.copy(authenticationState = VERIFIED)
  val tcAliceTampered = tcAliceUnverified.copy(authenticationState = TAMPERED)

  val tcBobUnverified = EndorsedTrustedContact(
    relationshipId = "rel-456",
    trustedContactAlias = TrustedContactAlias("bob"),
    authenticationState = AWAITING_VERIFY,
    keyCertificate = TrustedContactKeyCertificateFake2,
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )
  val tcBobVerified = tcBobUnverified.copy(authenticationState = VERIFIED)

  val appSessionManager = AppSessionManagerFake()
  val accountService = AccountServiceFake()
  val accountConfigService = AccountConfigServiceFake()
  val clock = ClockFake()

  fun TestScope.relationshipsService(backgroundScope: CoroutineScope): RelationshipsServiceImpl {
    relationshipsF8eFake = RelationshipsF8eClientFake(
      uuidGenerator = { "fake-uuid" },
      backgroundScope = backgroundScope,
      clock = clock
    )
    return RelationshipsServiceImpl(
      relationshipsF8eClientProvider = { relationshipsF8eFake },
      relationshipsDao = dao,
      relationshipsEnrollmentAuthenticationDao = authDao,
      relationshipsCrypto = relationshipsCrypto,
      relationshipsCodeBuilder = RelationshipsCodeBuilderFake(),
      appSessionManager = appSessionManager,
      accountService = accountService,
      appCoroutineScope = backgroundScope,
      clock = clock,
      relationshipsSyncFrequency = RelationshipsSyncFrequency(100.milliseconds),
      accountConfigService = accountConfigService
    )
  }

  afterTest {
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    accountConfigService.reset()
    accountConfigService.setActiveConfig(FullAccountMock.config)
    appKeyDao.reset()
    appSessionManager.reset()
    dao.clear()
    relationshipsCrypto.reset()
    relationshipsCrypto.reset()
    relationshipsF8eFake.reset()
  }

  test("sync relationships when db is changed") {
    val backgroundScope = createBackgroundScope()
    val service = relationshipsService(backgroundScope)

    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.filterNotNull().first().shouldBeEmpty()

    relationshipsCrypto.validCertificates += tcAliceUnverified.keyCertificate
    dao.setRelationships(
      Relationships.EMPTY.copy(
        endorsedTrustedContacts = immutableListOf(tcAliceVerified)
      )
    )

    service.relationships
      .filterNotNull()
      .first { !it.isEmpty() }
      .shouldOnlyHaveEndorsed(tcAliceVerified)
  }

  test("on demand sync and verify relationships") {
    val backgroundScope = createBackgroundScope()
    val service = relationshipsService(backgroundScope)

    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitUntil(Relationships.EMPTY)

      // Mark tcAlice's cert as valid
      relationshipsCrypto.validCertificates += tcAliceUnverified.keyCertificate
      // Add tcAlice to f8e
      relationshipsF8eFake.endorsedTrustedContacts.add(tcAliceUnverified)

      // Sync and verify
      service.syncAndVerifyRelationships(FullAccountMock)

      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceVerified)
    }
  }

  test("sync and verify relationships from service with prefetch") {
    val backgroundScope = createBackgroundScope()
    val service = relationshipsService(backgroundScope)

    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitUntil(Relationships.EMPTY)

      // Mark tcAlice's cert as valid
      relationshipsCrypto.validCertificates += tcAliceUnverified.keyCertificate
      // Add tcAlice to f8e
      relationshipsF8eFake.endorsedTrustedContacts.add(tcAliceUnverified)

      // Sync and verify
      service.syncAndVerifyRelationships(FullAccountMock)

      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceVerified)
    }
  }

  test("invalid Recovery Contacts are marked as tampered") {
    val backgroundScope = createBackgroundScope()
    val service = relationshipsService(backgroundScope)

    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitUntil(Relationships.EMPTY)

      // Mark tcAlice's cert as invalid
      relationshipsCrypto.invalidCertificates += tcAliceUnverified.keyCertificate
      // Mark tcBob's cert as valid
      relationshipsCrypto.validCertificates += tcBobUnverified.keyCertificate

      // Add both to f8e
      relationshipsF8eFake.endorsedTrustedContacts += tcAliceUnverified
      relationshipsF8eFake.endorsedTrustedContacts += tcBobUnverified

      service.syncAndVerifyRelationships(FullAccountMock)

      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceTampered, tcBobVerified)
    }
  }

  test("syncing does not occur while app is in the background") {
    val backgroundScope = createBackgroundScope()
    val service = relationshipsService(backgroundScope)

    appSessionManager.appDidEnterBackground()
    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitUntil(Relationships.EMPTY)

      // Mark tcAlice's cert as invalid
      relationshipsCrypto.invalidCertificates += tcAliceUnverified.keyCertificate
      // Mark tcBob's cert as valid
      relationshipsCrypto.validCertificates += tcBobUnverified.keyCertificate

      // Add both to f8e
      relationshipsF8eFake.endorsedTrustedContacts += tcAliceUnverified
      relationshipsF8eFake.endorsedTrustedContacts += tcBobUnverified

      // App is still in background
      expectNoEvents()

      appSessionManager.appDidEnterForeground()
      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceTampered, tcBobVerified)
    }
  }

  test("retrieveInvitation returns ExpiredInvitationCode when invitation is expired") {
    val backgroundScope = createBackgroundScope()
    val service = relationshipsService(backgroundScope)

    val currentTime = clock.now()
    val expiredTime = currentTime.minus(1.days)

    relationshipsF8eFake.invitationExpirationOverride = expiredTime

    val result = service.retrieveInvitation(
      account = FullAccountMock,
      invitationCode = "deadbeef,server123"
    )

    result.shouldBeErrOfType<RetrieveInvitationCodeError.ExpiredInvitationCode>()
  }
})
