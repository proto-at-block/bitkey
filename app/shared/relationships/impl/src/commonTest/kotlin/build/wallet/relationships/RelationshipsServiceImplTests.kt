@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.relationships

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.relationships.*
import build.wallet.sqldelight.InMemorySqlDriverFactory
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RelationshipsServiceImplTests : FunSpec({

  coroutineTestScope = true

  val databaseProvider = BitkeyDatabaseProviderImpl(
    InMemorySqlDriverFactory()
  )
  val dao = RelationshipsDaoImpl(databaseProvider)
  val appKeyDao = AppPrivateKeyDaoFake()
  val authDao = RelationshipsEnrollmentAuthenticationDaoImpl(appKeyDao, databaseProvider)

  lateinit var relationshipsF8eFake: RelationshipsF8eClientFake

  fun TestScope.relationshipsF8eClientFake() =
    RelationshipsF8eClientFake(
      uuidGenerator = { "fake-uuid" },
      backgroundScope = backgroundScope
    )

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

  fun TestScope.relationshipsService(): RelationshipsServiceImpl {
    relationshipsF8eFake = relationshipsF8eClientFake()
    return RelationshipsServiceImpl(
      relationshipsF8eClientProvider = suspend { relationshipsF8eFake },
      relationshipsDao = dao,
      relationshipsEnrollmentAuthenticationDao = authDao,
      relationshipsCrypto = relationshipsCrypto,
      relationshipsCodeBuilder = RelationshipsCodeBuilderFake(),
      appSessionManager = appSessionManager,
      accountService = accountService
    )
  }

  afterTest {
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    appKeyDao.reset()
    appSessionManager.reset()
    dao.clear()
    relationshipsCrypto.reset()
    relationshipsCrypto.reset()
    relationshipsF8eFake.reset()
  }

  // TODO(W-6203): this test is racy because syncLoop overwrites the dao with f8e data in the loop.
  xtest("sync relationships when db is changed") {
    /**
     * TODO: Can't use kotest's test scope due to a bug in kotest
     * https://github.com/kotest/kotest/pull/3717#issuecomment-1858174448
     *
     * This should be fixed in 5.9.0
     */
    val service = relationshipsService()

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
    val service = relationshipsService()

    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitItem().shouldBeNull() // initial loading

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
    val service = relationshipsService()

    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitItem().shouldBeNull() // initial loading

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

  test("invalid trusted contacts are marked as tampered") {
    val service = relationshipsService()

    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitItem().shouldBeNull() // initial loading

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
    val service = relationshipsService()

    appSessionManager.appDidEnterBackground()
    backgroundScope.launch {
      service.executeWork()
    }

    service.relationships.test {
      awaitItem().shouldBeNull() // initial loading

      // Mark tcAlice's cert as invalid
      relationshipsCrypto.invalidCertificates += tcAliceUnverified.keyCertificate
      // Mark tcBob's cert as valid
      relationshipsCrypto.validCertificates += tcBobUnverified.keyCertificate

      // Add both to f8e
      relationshipsF8eFake.endorsedTrustedContacts += tcAliceUnverified
      relationshipsF8eFake.endorsedTrustedContacts += tcBobUnverified

      appSessionManager.appDidEnterForeground()
      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceTampered, tcBobVerified)
    }
  }
})
