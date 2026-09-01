package bitkey.securitycenter

import app.cash.turbine.test
import bitkey.f8e.privilegedactions.DelayNotifyPeriodF8eClientFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DelayNotifyConfigurationServiceImplTest : FunSpec({
  val delayNotifyPeriodF8eClient = DelayNotifyPeriodF8eClientFake()
  val delayNotifyConfigurationDao = DelayNotifyConfigurationDaoFake()
  val service = DelayNotifyConfigurationServiceImpl(
    delayNotifyPeriodF8eClient = delayNotifyPeriodF8eClient,
    delayNotifyConfigurationDao = delayNotifyConfigurationDao
  )

  beforeTest {
    delayNotifyPeriodF8eClient.reset()
    delayNotifyConfigurationDao.reset()
  }

  test("loads uncached period from server and caches successful updates") {
    val proof = PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("signed-token"))
    delayNotifyPeriodF8eClient.periodDays = 14

    service.delayNotifyPeriod(FullAccountMock).test {
      awaitItem().shouldBe(null)
      awaitItem().shouldBe(14)
      delayNotifyPeriodF8eClient.getDelayNotifyPeriodCalls.shouldBe(
        listOf(FullAccountMock.accountId)
      )

      service.setDelayNotifyPeriod(
        account = FullAccountMock,
        delayPeriodDays = 30,
        proof = proof
      ).shouldBe(Ok(30))

      awaitItem().shouldBe(30)
    }
  }

  test("emits cached period without fetching from server") {
    delayNotifyConfigurationDao.setDelayNotifyPeriod(
      accountId = FullAccountMock.accountId,
      delayPeriodDays = 30
    )
    delayNotifyPeriodF8eClient.periodDays = 14

    service.delayNotifyPeriod(FullAccountMock).test {
      awaitItem().shouldBe(30)
      delayNotifyPeriodF8eClient.getDelayNotifyPeriodCalls.shouldBe(emptyList())
    }
  }

  test("sync updates cached period from server") {
    delayNotifyConfigurationDao.setDelayNotifyPeriod(
      accountId = FullAccountMock.accountId,
      delayPeriodDays = 14
    )
    delayNotifyPeriodF8eClient.periodDays = 30

    service.delayNotifyPeriod(FullAccountMock).test {
      awaitItem().shouldBe(14)

      service.syncDelayNotifyPeriod(FullAccountMock).shouldBe(Ok(30))

      awaitItem().shouldBe(30)
      delayNotifyPeriodF8eClient.getDelayNotifyPeriodCalls.shouldBe(
        listOf(FullAccountMock.accountId)
      )
    }
  }
})
