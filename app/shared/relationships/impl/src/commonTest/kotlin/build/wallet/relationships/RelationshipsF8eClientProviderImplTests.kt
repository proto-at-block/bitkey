package build.wallet.relationships

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.f8e.relationships.RelationshipsF8eClientImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope

class RelationshipsF8eClientProviderImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val debugOptionsService = DebugOptionsServiceFake()

  val get = RelationshipsF8eClientImpl(
    f8eHttpClient = F8eHttpClientMock(WsmVerifierMock())
  )
  val socRecFake = RelationshipsF8eClientFake(
    uuidGenerator = { "fake-uuid" },
    backgroundScope = TestScope()
  )
  val provider = RelationshipsF8eClientProviderImpl(
    accountService = accountService,
    debugOptionsService = debugOptionsService,
    relationshipsFake = socRecFake,
    relationshipsF8eClient = get
  )

  beforeTest {
    accountService.reset()
    debugOptionsService.reset()
  }

  test("use the real service based on debug options when no active account ") {
    debugOptionsService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use the fake service based on debug options when no active account ") {
    debugOptionsService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with active account") {
    accountService.setActiveAccount(FullAccountMock)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with active account") {
    accountService.setActiveAccount(LiteAccountMock)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with onboarding account") {
    accountService.saveAccountAndBeginOnboarding(FullAccountMock)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with onboarding account") {
    accountService.saveAccountAndBeginOnboarding(LiteAccountMock)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with no account") {
    debugOptionsService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with no account") {
    debugOptionsService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }
})
