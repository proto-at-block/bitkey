@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.recovery.socrec

import app.cash.turbine.test
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake2
import build.wallet.compose.collections.immutableListOf
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.f8e.socrec.SocialRecoveryServiceFake
import build.wallet.f8e.socrec.isEmpty
import build.wallet.f8e.socrec.shouldBeEmpty
import build.wallet.f8e.socrec.shouldOnlyHaveEndorsed
import build.wallet.sqldelight.InMemorySqlDriverFactory
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SocRecRelationshipsRepositoryImplTests : FunSpec({

  val databaseProvider = BitkeyDatabaseProviderImpl(
    InMemorySqlDriverFactory()
  )
  val dao = SocRecRelationshipsDaoImpl(databaseProvider)
  val appKeyDao = AppPrivateKeyDaoFake()
  val authDao = SocRecEnrollmentAuthenticationDaoImpl(appKeyDao, databaseProvider)

  val socRecFake =
    SocialRecoveryServiceFake(
      uuid = { "fake-uuid" },
      backgroundScope = TestScope()
    )

  val socRecCrypto = SocRecCryptoFake()

  beforeTest {
    socRecFake.reset()
    appKeyDao.reset()
    dao.clear()
    socRecCrypto.reset()
  }

  val tcAliceUnverified = TrustedContact(
    recoveryRelationshipId = "rel-123",
    trustedContactAlias = TrustedContactAlias("alice"),
    authenticationState = AWAITING_VERIFY,
    keyCertificate = TrustedContactKeyCertificateFake
  )
  val tcAliceVerified = tcAliceUnverified.copy(authenticationState = VERIFIED)
  val tcAliceTampered = tcAliceUnverified.copy(authenticationState = TAMPERED)

  val tcBobUnverified = TrustedContact(
    recoveryRelationshipId = "rel-456",
    trustedContactAlias = TrustedContactAlias("bob"),
    authenticationState = AWAITING_VERIFY,
    keyCertificate = TrustedContactKeyCertificateFake2
  )
  val tcBobVerified = tcBobUnverified.copy(authenticationState = VERIFIED)

  fun socRecRelationshipsRepository() =
    SocRecRelationshipsRepositoryImpl(
      socialRecoveryServiceProvider = suspend { socRecFake },
      socRecRelationshipsDao = dao,
      socRecEnrollmentAuthenticationDao = authDao,
      socRecCrypto = socRecCrypto,
      socialRecoveryCodeBuilder = SocialRecoveryCodeBuilderFake()
    )

  // TODO(W-6203): this test is racy because syncLoop overwrites the dao with f8e data in the loop.
  xtest("sync relationships when db is changed") {
    /**
     * TODO: Can't use kotest's test scope due to a bug in kotest
     * https://github.com/kotest/kotest/pull/3717#issuecomment-1858174448
     *
     * This should be fixed in 5.9.0
     */
    runTest {
      val repo = socRecRelationshipsRepository()

      repo.syncLoop(backgroundScope, FullAccountMock)

      repo.relationships.first().shouldBeEmpty()

      socRecCrypto.validCertificates += tcAliceUnverified.keyCertificate
      dao.setSocRecRelationships(
        SocRecRelationships.EMPTY.copy(
          trustedContacts = immutableListOf(tcAliceVerified)
        )
      )

      repo.relationships
        .first { !it.isEmpty() }
        .shouldOnlyHaveEndorsed(tcAliceVerified)
    }
  }

  test("on demand sync and verify relationships") {
    runTest {
      val repo = socRecRelationshipsRepository()

      repo.relationships.test {
        // Not awaiting the first item because it will never emit

        // Mark tcAlice's cert as valid
        socRecCrypto.validCertificates += tcAliceUnverified.keyCertificate
        // Add tcAlice to f8e
        socRecFake.trustedContacts.add(tcAliceUnverified)

        // Sync and verify
        repo.syncAndVerifyRelationships(FullAccountMock)

        awaitItem().shouldOnlyHaveEndorsed(tcAliceVerified)
      }
    }
  }

  test("sync and verify relationships from service with prefetch") {
    runTest {
      val repo = socRecRelationshipsRepository()

      repo.syncLoop(backgroundScope, FullAccountMock)

      repo.relationships.test {
        awaitItem().shouldBeEmpty()

        // Mark tcAlice's cert as valid
        socRecCrypto.validCertificates += tcAliceUnverified.keyCertificate
        // Add tcAlice to f8e
        socRecFake.trustedContacts.add(tcAliceUnverified)

        // Sync and verify
        repo.syncAndVerifyRelationships(FullAccountMock)

        awaitItem().shouldOnlyHaveEndorsed(tcAliceVerified)
      }
    }
  }

  test("sync relationship without verification") {
    runTest {
      val repo = socRecRelationshipsRepository()

      repo.relationships.test {
        // Add tcAlice to f8e
        socRecFake.trustedContacts.add(tcAliceUnverified)

        // Sync without verification
        repo.syncRelationshipsWithoutVerification(
          FullAccountMock.accountId,
          FullAccountMock.config.f8eEnvironment
        )

        awaitItem().shouldOnlyHaveEndorsed(tcAliceUnverified)
      }
    }
  }

  test("invalid trusted contacts are marked as tampered") {
    runTest {
      val repo = socRecRelationshipsRepository()

      repo.syncLoop(backgroundScope, FullAccountMock)

      repo.relationships.test {
        awaitItem().shouldBeEmpty()

        // Mark tcAlice's cert as invalid
        socRecCrypto.invalidCertificates += tcAliceUnverified.keyCertificate
        // Mark tcBob's cert as valid
        socRecCrypto.validCertificates += tcBobUnverified.keyCertificate

        // Add both to f8e
        socRecFake.trustedContacts += tcAliceUnverified
        socRecFake.trustedContacts += tcBobUnverified

        repo.syncAndVerifyRelationships(FullAccountMock)

        awaitItem().shouldOnlyHaveEndorsed(
          tcAliceTampered,
          tcBobVerified
        )
      }
    }
  }
})
