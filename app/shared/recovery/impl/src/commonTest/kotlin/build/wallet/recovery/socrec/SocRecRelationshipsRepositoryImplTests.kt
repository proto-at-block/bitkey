package build.wallet.recovery.socrec

import build.wallet.account.AccountRepositoryFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.compose.collections.immutableListOf
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.f8e.socrec.SocialRecoveryServiceFake
import build.wallet.f8e.socrec.SocialRecoveryServiceImpl
import build.wallet.keybox.config.TemplateKeyboxConfigDaoFake
import build.wallet.sqldelight.InMemorySqlDriverFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SocRecRelationshipsRepositoryImplTests : FunSpec({

  val accountRepository = AccountRepositoryFake()
  val dao =
    SocRecRelationshipsDaoImpl(
      BitkeyDatabaseProviderImpl(
        InMemorySqlDriverFactory()
      )
    )

  val socRecService =
    SocialRecoveryServiceImpl(
      f8eHttpClient = F8eHttpClientMock()
    )

  val socRecFake =
    SocialRecoveryServiceFake(
      uuid = { "fake-uuid" },
      backgroundScope = TestScope()
    )

  val templateKeyboxConfigDao = TemplateKeyboxConfigDaoFake()

  beforeTest {
    accountRepository.reset()
    socRecFake.reset()
    templateKeyboxConfigDao.reset()
  }

  fun makeSocRecRelationshipsRepositoryImpl(scope: CoroutineScope) =
    SocRecRelationshipsRepositoryImpl(
      accountRepository = accountRepository,
      socRecRelationshipsDao = dao,
      socRecFake = socRecFake,
      socRecService = socRecService,
      scope = scope,
      templateKeyboxConfigDao = templateKeyboxConfigDao
    )

  test("relationships load from db without prefetch") {
    /**
     * TODO: Can't use kotest's test scope due to a bug in kotest
     * https://github.com/kotest/kotest/pull/3717#issuecomment-1858174448
     *
     * This should be fixed in 5.9.0
     */
    val scope = TestScope()
    scope.runTest {

      val repo = makeSocRecRelationshipsRepositoryImpl(scope.backgroundScope)

      val tc =
        TrustedContact(
          recoveryRelationshipId = "rel-123",
          trustedContactAlias = TrustedContactAlias("bob"),
          identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("abcdefg"))
        )
      dao.setSocRecRelationships(
        SocRecRelationships(
          listOf(),
          listOf(tc),
          immutableListOf(),
          listOf()
        )
      )

      scope.testScheduler.advanceUntilIdle()

      repo.relationships.take(2).last()
        .trustedContacts
        .shouldHaveSingleElement(tc)
    }
  }

  test("relationships load from service with prefetch") {
    val scope = TestScope()
    scope.runTest {
      val repo = makeSocRecRelationshipsRepositoryImpl(scope.backgroundScope)

      val tc =
        TrustedContact(
          recoveryRelationshipId = "rel-123",
          trustedContactAlias = TrustedContactAlias("bob"),
          identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("abcdefg"))
        )
      socRecFake.trustedContacts.add(tc)
      backgroundScope.launch {
        repo.syncLoop(FullAccountMock)
      }

      repo.relationships.take(2).last()
        .trustedContacts
        .shouldHaveSingleElement(tc)

      repo.relationships.value
        .trustedContacts
        .shouldHaveSingleElement(tc)
    }
  }

  test("use the real service by default") {
    val repo = makeSocRecRelationshipsRepositoryImpl(TestScope())
    repo.socRecService().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use the real service when isUsingSocRecFakes is false with active account") {
    val repo = makeSocRecRelationshipsRepositoryImpl(TestScope())
    accountRepository.setActiveAccount(FullAccountMock)
    repo.socRecService().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with active account") {
    val repo = makeSocRecRelationshipsRepositoryImpl(TestScope())
    accountRepository.setActiveAccount(LiteAccountMock)
    repo.socRecService().shouldBeInstanceOf<SocialRecoveryServiceFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with onboarding account") {
    val repo = makeSocRecRelationshipsRepositoryImpl(TestScope())
    accountRepository.saveAccountAndBeginOnboarding(FullAccountMock)
    repo.socRecService().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with onboarding account") {
    val repo = makeSocRecRelationshipsRepositoryImpl(TestScope())
    accountRepository.saveAccountAndBeginOnboarding(LiteAccountMock)
    repo.socRecService().shouldBeInstanceOf<SocialRecoveryServiceFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with no account") {
    val repo = makeSocRecRelationshipsRepositoryImpl(TestScope())
    templateKeyboxConfigDao.set(KeyboxConfigMock.copy(isUsingSocRecFakes = false))
    repo.socRecService().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with no account") {
    val repo = makeSocRecRelationshipsRepositoryImpl(TestScope())
    templateKeyboxConfigDao.set(KeyboxConfigMock.copy(isUsingSocRecFakes = true))
    repo.socRecService().shouldBeInstanceOf<SocialRecoveryServiceFake>()
  }
})
