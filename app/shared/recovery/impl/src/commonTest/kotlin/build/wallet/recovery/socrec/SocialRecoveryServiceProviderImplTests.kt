package build.wallet.recovery.socrec

import build.wallet.account.AccountRepositoryFake
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.f8e.socrec.SocialRecoveryServiceFake
import build.wallet.f8e.socrec.SocialRecoveryServiceImpl
import build.wallet.keybox.config.TemplateFullAccountConfigDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope

class SocialRecoveryServiceProviderImplTests : FunSpec({
  val accountRepository = AccountRepositoryFake()
  val templateFullAccountConfigDao = TemplateFullAccountConfigDaoFake()

  val get =
    SocialRecoveryServiceImpl(
      f8eHttpClient = F8eHttpClientMock(WsmVerifierMock())
    )
  val socRecFake =
    SocialRecoveryServiceFake(
      uuid = { "fake-uuid" },
      backgroundScope = TestScope()
    )
  val provider = SocialRecoveryServiceProviderImpl(
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
    provider.get().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use the real service when isUsingSocRecFakes is false with active account") {
    accountRepository.setActiveAccount(FullAccountMock)
    provider.get().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with active account") {
    accountRepository.setActiveAccount(LiteAccountMock)
    provider.get().shouldBeInstanceOf<SocialRecoveryServiceFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with onboarding account") {
    accountRepository.saveAccountAndBeginOnboarding(FullAccountMock)
    provider.get().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with onboarding account") {
    accountRepository.saveAccountAndBeginOnboarding(LiteAccountMock)
    provider.get().shouldBeInstanceOf<SocialRecoveryServiceFake>()
  }

  test("use the real service when isUsingSocRecFakes is false with no account") {
    templateFullAccountConfigDao.set(FullAccountConfigMock.copy(isUsingSocRecFakes = false))
    provider.get().shouldBeInstanceOf<SocialRecoveryServiceImpl>()
  }

  test("use fakes when isUsingSocRecFakes is true with no account") {
    templateFullAccountConfigDao.set(FullAccountConfigMock.copy(isUsingSocRecFakes = true))
    provider.get().shouldBeInstanceOf<SocialRecoveryServiceFake>()
  }
})
