package build.wallet.recovery.socrec

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.f8e.socrec.SocRecF8eClientFake
import build.wallet.f8e.socrec.SocRecF8eClientImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class SocialRecoveryF8eClientProviderImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val debugOptionsService = DebugOptionsServiceFake()

  val get = SocRecF8eClientImpl(
    f8eHttpClient = F8eHttpClientMock(WsmVerifierMock())
  )
  val socRecFake = SocRecF8eClientFake(
    uuidGenerator = { "fake-uuid" }
  )
  val provider = SocRecF8eClientProviderImpl(
    accountService,
    debugOptionsService,
    socRecFake,
    get
  )

  beforeTest {
    accountService.reset()
    debugOptionsService.reset()
  }

  test("use the real service based on debug options when no active account ") {
    debugOptionsService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use the fake service based on debug options when no active account ") {
    debugOptionsService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with active account") {
    accountService.setActiveAccount(FullAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with active account") {
    accountService.setActiveAccount(LiteAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with onboarding account") {
    accountService.saveAccountAndBeginOnboarding(FullAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with onboarding account") {
    accountService.saveAccountAndBeginOnboarding(LiteAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with no account") {
    debugOptionsService.setUsingSocRecFakes(value = false)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with no account") {
    debugOptionsService.setUsingSocRecFakes(value = true)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }
})
