package bitkey.securitycenter

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.ConfigurableDelayNotifyFeatureFlag
import build.wallet.feature.flags.ConfigurableDelayNotifyW3MinFirmwareVersionFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DelayNotifyPeriodActionFactoryImplTest : FunSpec({
  val accountService = AccountServiceFake()
  val delayNotifyConfigurationService = AccountScopedDelayNotifyConfigurationService()
  val configurableDelayNotifyFeatureFlag = ConfigurableDelayNotifyFeatureFlag(FeatureFlagDaoFake())
  val w3MinFirmwareVersionFeatureFlag =
    ConfigurableDelayNotifyW3MinFirmwareVersionFeatureFlag(FeatureFlagDaoFake())
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()
  val factory = DelayNotifyPeriodActionFactoryImpl(
    accountService = accountService,
    delayNotifyConfigurationService = delayNotifyConfigurationService,
    configurableDelayNotifyFeatureFlag = configurableDelayNotifyFeatureFlag,
    configurableDelayNotifyW3MinFirmwareVersionFeatureFlag = w3MinFirmwareVersionFeatureFlag,
    firmwareDeviceInfoDao = firmwareDeviceInfoDao
  )

  val w1DeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w1a-dvt")
  val w3DeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")

  beforeTest {
    accountService.reset()
    delayNotifyConfigurationService.reset()
    firmwareDeviceInfoDao.reset()
    configurableDelayNotifyFeatureFlag.reset()
    w3MinFirmwareVersionFeatureFlag.reset()
    configurableDelayNotifyFeatureFlag.setFlagValue(true)
  }

  test("fetches delay notify period for the active account") {
    val firstAccount = FullAccountMock
    val secondAccount = FullAccountMock.copy(
      accountId = FullAccountId("second-account")
    )
    delayNotifyConfigurationService.periodsByAccount[firstAccount.accountId] = 14
    delayNotifyConfigurationService.periodsByAccount[secondAccount.accountId] = 7

    factory.create().test {
      awaitItem().shouldBeNull()

      accountService.setActiveAccount(firstAccount)
      awaitDelayNotifyPeriodAction().currentPeriodDays.shouldBe(14)

      accountService.setActiveAccount(secondAccount)
      awaitDelayNotifyPeriodAction().currentPeriodDays.shouldBe(7)

      delayNotifyConfigurationService.getPeriodCalls.shouldBe(
        listOf(firstAccount.accountId, secondAccount.accountId)
      )
    }
  }

  test("returns no action when the configurable delay & notify flag is disabled") {
    configurableDelayNotifyFeatureFlag.setFlagValue(false)
    delayNotifyConfigurationService.periodsByAccount[FullAccountMock.accountId] = 14
    accountService.setActiveAccount(FullAccountMock)

    factory.create().test {
      awaitItem().shouldBeNull()
      delayNotifyConfigurationService.getPeriodCalls.shouldBe(emptyList())
    }
  }

  test("returns no action for W3 account when min firmware version is not set") {
    firmwareDeviceInfoDao.setDeviceInfo(w3DeviceInfo.copy(version = "1.2.3"))
    delayNotifyConfigurationService.periodsByAccount[FullAccountW3Mock.accountId] = 14
    accountService.setActiveAccount(FullAccountW3Mock)

    factory.create().test {
      awaitItem().shouldBeNull()
      delayNotifyConfigurationService.getPeriodCalls.shouldBe(emptyList())
    }
  }

  test("returns no action for W3 account when firmware is below the min version") {
    firmwareDeviceInfoDao.setDeviceInfo(w3DeviceInfo.copy(version = "1.2.3"))
    w3MinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.3.0"))
    delayNotifyConfigurationService.periodsByAccount[FullAccountW3Mock.accountId] = 14
    accountService.setActiveAccount(FullAccountW3Mock)

    factory.create().test {
      awaitItem().shouldBeNull()
      delayNotifyConfigurationService.getPeriodCalls.shouldBe(emptyList())
    }
  }

  test("returns action for W3 account when firmware meets the min version") {
    firmwareDeviceInfoDao.setDeviceInfo(w3DeviceInfo.copy(version = "1.3.0"))
    w3MinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.3.0"))
    delayNotifyConfigurationService.periodsByAccount[FullAccountW3Mock.accountId] = 14
    accountService.setActiveAccount(FullAccountW3Mock)

    factory.create().test {
      awaitDelayNotifyPeriodAction().currentPeriodDays.shouldBe(14)
    }
  }

  test("returns no action for W3 account when firmware device info is unavailable") {
    w3MinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.3.0"))
    delayNotifyConfigurationService.periodsByAccount[FullAccountW3Mock.accountId] = 14
    accountService.setActiveAccount(FullAccountW3Mock)

    factory.create().test {
      awaitItem().shouldBeNull()
      delayNotifyConfigurationService.getPeriodCalls.shouldBe(emptyList())
    }
  }

  test("returns no action for W3 account when cached device info is stale W1 metadata") {
    // Post W1 -> W3 upgrade state: dao still holds W1 info with a satisfying version.
    firmwareDeviceInfoDao.setDeviceInfo(w1DeviceInfo.copy(version = "9.0.0"))
    w3MinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.3.0"))
    delayNotifyConfigurationService.periodsByAccount[FullAccountW3Mock.accountId] = 14
    accountService.setActiveAccount(FullAccountW3Mock)

    factory.create().test {
      awaitItem().shouldBeNull()
      delayNotifyConfigurationService.getPeriodCalls.shouldBe(emptyList())
    }
  }

  test("returns no action for W3 account when the min version flag is malformed") {
    firmwareDeviceInfoDao.setDeviceInfo(w3DeviceInfo.copy(version = "1.3.0"))
    w3MinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.3"))
    delayNotifyConfigurationService.periodsByAccount[FullAccountW3Mock.accountId] = 14
    accountService.setActiveAccount(FullAccountW3Mock)

    factory.create().test {
      awaitItem().shouldBeNull()
      delayNotifyConfigurationService.getPeriodCalls.shouldBe(emptyList())
    }
  }

  test("returns action for W1 account regardless of the W3 min firmware version flag") {
    // No device info stored and an unsatisfiable W3 min version: W1 must be unaffected.
    w3MinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("9.9.9"))
    delayNotifyConfigurationService.periodsByAccount[FullAccountMock.accountId] = 14
    accountService.setActiveAccount(FullAccountMock)

    factory.create().test {
      awaitDelayNotifyPeriodAction().currentPeriodDays.shouldBe(14)
    }
  }
})

private suspend fun ReceiveTurbine<SecurityAction?>.awaitDelayNotifyPeriodAction() =
  awaitItem() as DelayNotifyPeriodAction

private class AccountScopedDelayNotifyConfigurationService : DelayNotifyConfigurationService {
  val periodsByAccount = mutableMapOf<FullAccountId, Int?>()
  val getPeriodCalls = mutableListOf<FullAccountId>()

  override fun delayNotifyPeriod(account: FullAccount): Flow<Int?> = flow {
    getPeriodCalls.add(account.accountId)
    emit(periodsByAccount.getValue(account.accountId))
  }

  override suspend fun syncDelayNotifyPeriod(account: FullAccount): Result<Int, NetworkingError> {
    return Ok(periodsByAccount.getValue(account.accountId)!!)
  }

  override suspend fun setDelayNotifyPeriod(
    account: FullAccount,
    delayPeriodDays: Int,
    proof: PrivilegedActionProof?,
  ): Result<Int, NetworkingError> {
    return Ok(delayPeriodDays)
  }

  fun reset() {
    periodsByAccount.clear()
    getPeriodCalls.clear()
  }
}
