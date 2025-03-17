package build.wallet.recovery.socrec

import bitkey.account.AccountConfigServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.f8e.socrec.SocRecF8eClientFake
import build.wallet.f8e.socrec.SocRecF8eClientImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class SocialRecoveryF8eClientProviderImplTests : FunSpec({
  val defaultAppConfigService = AccountConfigServiceFake()

  val get = SocRecF8eClientImpl(
    f8eHttpClient = F8eHttpClientMock(WsmVerifierMock())
  )
  val socRecFake = SocRecF8eClientFake(
    uuidGenerator = { "fake-uuid" }
  )
  val provider = SocRecF8eClientProviderImpl(
    defaultAppConfigService,
    socRecFake,
    get
  )

  beforeTest {
    defaultAppConfigService.reset()
  }

  test("use the real service based on debug options when no active account ") {
    defaultAppConfigService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use the fake service based on debug options when no active account ") {
    defaultAppConfigService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with active account") {
    defaultAppConfigService.setActiveConfig(FullAccountMock.config)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with active account") {
    defaultAppConfigService.setActiveConfig(LiteAccountMock.config)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with onboarding account") {
    defaultAppConfigService.setActiveConfig(FullAccountMock.config)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with onboarding account") {
    defaultAppConfigService.setActiveConfig(LiteAccountMock.config)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with no account") {
    defaultAppConfigService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with no account") {
    defaultAppConfigService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }
})
