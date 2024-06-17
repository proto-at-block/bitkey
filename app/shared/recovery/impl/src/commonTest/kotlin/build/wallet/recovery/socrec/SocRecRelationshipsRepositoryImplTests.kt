package build.wallet.recovery.socrec

import app.cash.turbine.test
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.socrec.EndorsedTrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake2
import build.wallet.compose.collections.immutableListOf
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.socrec.SocRecF8eClientFake
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.f8e.socrec.isEmpty
import build.wallet.f8e.socrec.shouldBeEmpty
import build.wallet.f8e.socrec.shouldOnlyHaveEndorsed
import build.wallet.sqldelight.InMemorySqlDriverFactory
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class SocRecRelationshipsRepositoryImplTests : FunSpec({

  coroutineTestScope = true

  val databaseProvider = BitkeyDatabaseProviderImpl(
    InMemorySqlDriverFactory()
  )
  val dao = SocRecRelationshipsDaoImpl(databaseProvider)
  val appKeyDao = AppPrivateKeyDaoFake()
  val authDao = SocRecEnrollmentAuthenticationDaoImpl(appKeyDao, databaseProvider)

  lateinit var socRecFake: SocRecF8eClientFake

  fun TestScope.socRecFake() =
    SocRecF8eClientFake(
      uuidGenerator = { "fake-uuid" },
      backgroundScope = backgroundScope
    )

  val socRecCrypto = SocRecCryptoFake()

  afterTest {
    socRecFake.reset()
    appKeyDao.reset()
    dao.clear()
    socRecCrypto.reset()
  }

  val tcAliceUnverified = EndorsedTrustedContact(
    recoveryRelationshipId = "rel-123",
    trustedContactAlias = TrustedContactAlias("alice"),
    authenticationState = AWAITING_VERIFY,
    keyCertificate = TrustedContactKeyCertificateFake
  )
  val tcAliceVerified = tcAliceUnverified.copy(authenticationState = VERIFIED)
  val tcAliceTampered = tcAliceUnverified.copy(authenticationState = TAMPERED)

  val tcBobUnverified = EndorsedTrustedContact(
    recoveryRelationshipId = "rel-456",
    trustedContactAlias = TrustedContactAlias("bob"),
    authenticationState = AWAITING_VERIFY,
    keyCertificate = TrustedContactKeyCertificateFake2
  )
  val tcBobVerified = tcBobUnverified.copy(authenticationState = VERIFIED)

  val appSessionManager = AppSessionManagerFake()

  fun TestScope.socRecRelationshipsRepository(): SocRecRelationshipsRepositoryImpl {
    socRecFake = socRecFake()
    return SocRecRelationshipsRepositoryImpl(
      appScope = backgroundScope,
      socRecF8eClientProvider = suspend { socRecFake },
      socRecRelationshipsDao = dao,
      socRecEnrollmentAuthenticationDao = authDao,
      socRecCrypto = socRecCrypto,
      socialRecoveryCodeBuilder = SocialRecoveryCodeBuilderFake(),
      appSessionManager = appSessionManager
    )
  }

  // TODO(W-6203): this test is racy because syncLoop overwrites the dao with f8e data in the loop.
  xtest("sync relationships when db is changed") {
    /**
     * TODO: Can't use kotest's test scope due to a bug in kotest
     * https://github.com/kotest/kotest/pull/3717#issuecomment-1858174448
     *
     * This should be fixed in 5.9.0
     */
    val repo = socRecRelationshipsRepository()

    repo.syncLoop(backgroundScope, FullAccountMock)

    repo.relationships.filterNotNull().first().shouldBeEmpty()

    socRecCrypto.validCertificates += tcAliceUnverified.keyCertificate
    dao.setSocRecRelationships(
      SocRecRelationships.EMPTY.copy(
        endorsedTrustedContacts = immutableListOf(tcAliceVerified)
      )
    )

    repo.relationships
      .filterNotNull()
      .first { !it.isEmpty() }
      .shouldOnlyHaveEndorsed(tcAliceVerified)
  }

  test("on demand sync and verify relationships") {
    val repo = socRecRelationshipsRepository()

    repo.relationships.test {
      awaitItem().shouldBeNull() // initial loading

      // Mark tcAlice's cert as valid
      socRecCrypto.validCertificates += tcAliceUnverified.keyCertificate
      // Add tcAlice to f8e
      socRecFake.endorsedTrustedContacts.add(tcAliceUnverified)

      // Sync and verify
      repo.syncAndVerifyRelationships(FullAccountMock)

      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceVerified)
    }
  }

  test("sync and verify relationships from service with prefetch") {
    val repo = socRecRelationshipsRepository()

    repo.syncLoop(backgroundScope, FullAccountMock)

    repo.relationships.test {
      awaitItem().shouldBeNull() // initial loading

      // Mark tcAlice's cert as valid
      socRecCrypto.validCertificates += tcAliceUnverified.keyCertificate
      // Add tcAlice to f8e
      socRecFake.endorsedTrustedContacts.add(tcAliceUnverified)

      // Sync and verify
      repo.syncAndVerifyRelationships(FullAccountMock)

      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceVerified)
    }
  }

  test("invalid trusted contacts are marked as tampered") {
    val repo = socRecRelationshipsRepository()

    repo.syncLoop(backgroundScope, FullAccountMock)

    repo.relationships.test {
      awaitItem().shouldBeNull() // initial loading

      // Mark tcAlice's cert as invalid
      socRecCrypto.invalidCertificates += tcAliceUnverified.keyCertificate
      // Mark tcBob's cert as valid
      socRecCrypto.validCertificates += tcBobUnverified.keyCertificate

      // Add both to f8e
      socRecFake.endorsedTrustedContacts += tcAliceUnverified
      socRecFake.endorsedTrustedContacts += tcBobUnverified

      repo.syncAndVerifyRelationships(FullAccountMock)

      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceTampered, tcBobVerified)
    }
  }

  test("syncing does not occur while app is in the background") {
    val repo = socRecRelationshipsRepository()

    appSessionManager.appDidEnterBackground()
    repo.syncLoop(backgroundScope, FullAccountMock)

    repo.relationships.test {
      awaitItem().shouldBeNull() // initial loading

      // Mark tcAlice's cert as invalid
      socRecCrypto.invalidCertificates += tcAliceUnverified.keyCertificate
      // Mark tcBob's cert as valid
      socRecCrypto.validCertificates += tcBobUnverified.keyCertificate

      // Add both to f8e
      socRecFake.endorsedTrustedContacts += tcAliceUnverified
      socRecFake.endorsedTrustedContacts += tcBobUnverified

      appSessionManager.appDidEnterForeground()
      awaitItem()
        .shouldNotBeNull()
        .shouldOnlyHaveEndorsed(tcAliceTampered, tcBobVerified)
    }
  }
})
