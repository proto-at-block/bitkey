package build.wallet.recovery.socrec

import build.wallet.account.AccountRepositoryFake
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.f8e.socrec.SocRecF8eClientFake
import build.wallet.f8e.socrec.SocRecF8eClientImpl
import build.wallet.keybox.config.TemplateFullAccountConfigDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope

class SocialRecoveryF8eClientProviderImplTests : FunSpec({
  val accountRepository = AccountRepositoryFake()
  val templateFullAccountConfigDao = TemplateFullAccountConfigDaoFake()

  val get =
    SocRecF8eClientImpl(
      f8eHttpClient = F8eHttpClientMock(WsmVerifierMock())
    )
  val socRecFake =
    SocRecF8eClientFake(
      uuidGenerator = { "fake-uuid" },
      backgroundScope = TestScope()
    )
  val provider = SocRecF8eClientProviderImpl(
    accountRepository,
    templateFullAccountConfigDao,
    socRecFake,
    get
  )

  beforeTest {
    accountRepository.reset()
    templateFullAccountConfigDao.reset()
  }

  test("use the real service by default") {
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use the real service when isUsingSocRecFakes is false with active account") {
    accountRepository.setActiveAccount(FullAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with active account") {
    accountRepository.setActiveAccount(LiteAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with onboarding account") {
    accountRepository.saveAccountAndBeginOnboarding(FullAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with onboarding account") {
    accountRepository.saveAccountAndBeginOnboarding(LiteAccountMock)
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with no account") {
    templateFullAccountConfigDao.set(FullAccountConfigMock.copy(isUsingSocRecFakes = false))
    provider.get().shouldBeInstanceOf<SocRecF8eClientImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with no account") {
    templateFullAccountConfigDao.set(FullAccountConfigMock.copy(isUsingSocRecFakes = true))
    provider.get().shouldBeInstanceOf<SocRecF8eClientFake>()
  }
})
