package build.wallet.relationships

import bitkey.account.AccountConfigServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.f8e.relationships.RelationshipsF8eClientImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Clock

class RelationshipsF8eClientProviderImplTests : FunSpec({
  val appConfigService = AccountConfigServiceFake()

  val socRecImpl = RelationshipsF8eClientImpl(
    f8eHttpClient = F8eHttpClientMock(WsmVerifierMock())
  )
  val socRecFake = RelationshipsF8eClientFake(
    uuidGenerator = { "fake-uuid" },
    backgroundScope = TestScope(),
    clock = Clock.System
  )
  val provider = RelationshipsF8eClientProviderImpl(
    accountConfigService = appConfigService,
    relationshipsF8eClientFake = socRecFake,
    relationshipsF8eClientImpl = socRecImpl
  )

  beforeTest {
    appConfigService.reset()
  }

  test("use the real service based on debug options when no active account ") {
    appConfigService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use the fake service based on debug options when no active account ") {
    appConfigService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with active account") {
    appConfigService.setActiveConfig(FullAccountMock.config)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with active account") {
    appConfigService.setActiveConfig(LiteAccountMock.config)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with onboarding account") {
    appConfigService.setActiveConfig(FullAccountMock.config)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with onboarding account") {
    appConfigService.setActiveConfig(LiteAccountMock.config)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with no account") {
    appConfigService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with no account") {
    appConfigService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<RelationshipsF8eClientFake>()
  }
})
