package build.wallet.notifications

import bitkey.account.AccountConfigServiceFake
import bitkey.auth.AuthTokenScope
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.KeyboxMock2
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.onboarding.AddDeviceTokenF8eClientMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.HttpError
import build.wallet.platform.config.DeviceTokenConfig
import build.wallet.platform.config.DeviceTokenConfigProviderMock
import build.wallet.platform.config.TouchpointPlatform
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.device.DevicePlatform
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class DeviceTokenManagerImplTests : FunSpec({

  val addDeviceTokenF8eClient = AddDeviceTokenF8eClientMock(turbines::create)
  val deviceTokenConfigProvider = DeviceTokenConfigProviderMock()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val accountConfigService = AccountConfigServiceFake()
  val accountService = AccountServiceFake()
  val deviceInfoProvider = DeviceInfoProviderMock()

  val manager = DeviceTokenManagerImpl(
    addDeviceTokenF8eClient = addDeviceTokenF8eClient,
    deviceTokenConfigProvider = deviceTokenConfigProvider,
    keyboxDao = keyboxDao,
    accountConfigService = accountConfigService,
    accountService = accountService,
    deviceInfoProvider = deviceInfoProvider
  )

  beforeTest {
    deviceTokenConfigProvider.reset()
    keyboxDao.reset()
    addDeviceTokenF8eClient.reset()
    deviceInfoProvider.reset()
    accountService.reset()
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount with active account") {
    val activeKeybox = KeyboxMock
    val token = "abcd"
    val touchpointPlatform = TouchpointPlatform.FcmTeam
    keyboxDao.activeKeybox.emit(Ok(activeKeybox))
    keyboxDao.onboardingKeybox.emit(Ok(null))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform = touchpointPlatform
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenF8eClient.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenF8eClientMock.AddParams>()
    ) {
      f8eEnvironment.shouldBe(activeKeybox.config.f8eEnvironment)
      fullAccountId.shouldBe(activeKeybox.fullAccountId)
      this.token.shouldBe(token)
      this.touchpointPlatform.shouldBe(touchpointPlatform)
    }
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount with onboarding account") {
    val onboardingKeybox = KeyboxMock
    val token = "abcd"
    val touchpointPlatform = TouchpointPlatform.FcmTeam
    keyboxDao.activeKeybox.emit(Ok(null))
    keyboxDao.onboardingKeybox.emit(Ok(onboardingKeybox))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform = touchpointPlatform
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenF8eClient.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenF8eClientMock.AddParams>()
    ) {
      f8eEnvironment.shouldBe(onboardingKeybox.config.f8eEnvironment)
      fullAccountId.shouldBe(onboardingKeybox.fullAccountId)
      this.token.shouldBe(token)
      this.touchpointPlatform.shouldBe(touchpointPlatform)
    }
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount with active and onboarding account") {
    val activeKeybox = KeyboxMock
    val onboardingKeybox = KeyboxMock2
    val token = "abcd"
    val touchpointPlatform = TouchpointPlatform.FcmTeam
    keyboxDao.activeKeybox.emit(Ok(activeKeybox))
    keyboxDao.onboardingKeybox.emit(Ok(onboardingKeybox))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform = touchpointPlatform
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenF8eClient.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenF8eClientMock.AddParams>()
    ) {
      f8eEnvironment.shouldBe(activeKeybox.config.f8eEnvironment)
      fullAccountId.shouldBe(activeKeybox.fullAccountId)
      this.token.shouldBe(token)
      this.touchpointPlatform.shouldBe(touchpointPlatform)
    }
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount with no account") {
    keyboxDao.activeKeybox.emit(Ok(null))
    keyboxDao.onboardingKeybox.emit(Ok(null))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = "abcd",
        touchpointPlatform = TouchpointPlatform.FcmTeam
      )

    result.shouldBe(DeviceTokenManagerResult.Err(DeviceTokenManagerError.NoKeybox))
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount network error") {
    keyboxDao.activeKeybox.emit(Ok(KeyboxMock))
    addDeviceTokenF8eClient.addResult = Err(HttpError.NetworkError(Throwable()))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = "abcd",
        touchpointPlatform = TouchpointPlatform.FcmTeam
      )

    addDeviceTokenF8eClient.addCalls.awaitItem()

    result
      .result
      .shouldBeErrOfType<DeviceTokenManagerError.NetworkingError>()
  }

  test("addDeviceTokenIfPresentForAccount with config") {
    val account = FullAccountId("123")
    val f8eEnvironment = F8eEnvironment.Development
    val token = "abcd"
    val touchpointPlatform = TouchpointPlatform.FcmTeam
    deviceTokenConfigProvider.configResult = DeviceTokenConfig(token, touchpointPlatform)
    val result =
      manager.addDeviceTokenIfPresentForAccount(
        fullAccountId = account,
        authTokenScope = AuthTokenScope.Global
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenF8eClient.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenF8eClientMock.AddParams>()
    ) {
      this.f8eEnvironment.shouldBe(f8eEnvironment)
      this.fullAccountId.shouldBe(account)
      this.token.shouldBe(token)
      this.touchpointPlatform.shouldBe(touchpointPlatform)
    }
  }

  test("addDeviceTokenIfPresentForAccount with no config") {
    val result =
      manager.addDeviceTokenIfPresentForAccount(
        fullAccountId = FullAccountId("123"),
        authTokenScope = AuthTokenScope.Global
      )

    result.shouldBe(DeviceTokenManagerResult.Err(DeviceTokenManagerError.NoDeviceToken))
  }

  test("addDeviceTokenIfPresentForAccount network error") {
    addDeviceTokenF8eClient.addResult = Err(HttpError.NetworkError(Throwable()))
    deviceTokenConfigProvider.configResult =
      DeviceTokenConfig(
        deviceToken = "abcd",
        touchpointPlatform = TouchpointPlatform.FcmTeam
      )
    val result = manager.addDeviceTokenIfPresentForAccount(
      fullAccountId = FullAccountId("123"),
      authTokenScope = AuthTokenScope.Global
    )

    addDeviceTokenF8eClient.addCalls.awaitItem()

    result
      .result
      .shouldBeErrOfType<DeviceTokenManagerError.NetworkingError>()
  }

  test("worker executes on Android platform with active account") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.Android
    accountService.setActiveAccount(FullAccountMock)
    deviceTokenConfigProvider.configResult = DeviceTokenConfig(
      deviceToken = "android-token",
      touchpointPlatform = TouchpointPlatform.FcmTeam
    )

    manager.executeWork()

    with(
      addDeviceTokenF8eClient.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenF8eClientMock.AddParams>()
    ) {
      fullAccountId.shouldBe(FullAccountMock.accountId)
      token.shouldBe("android-token")
      touchpointPlatform.shouldBe(TouchpointPlatform.FcmTeam)
    }
  }

  test("worker does not execute on iOS platform") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    accountService.setActiveAccount(FullAccountMock)
    deviceTokenConfigProvider.configResult = DeviceTokenConfig(
      deviceToken = "ios-token",
      touchpointPlatform = TouchpointPlatform.ApnsCustomer
    )

    manager.executeWork()

    addDeviceTokenF8eClient.addCalls.expectNoEvents()
  }

  test("worker does not execute on Android when no active account") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.Android
    accountService.reset()
    deviceTokenConfigProvider.configResult = DeviceTokenConfig(
      deviceToken = "android-token",
      touchpointPlatform = TouchpointPlatform.FcmTeam
    )

    manager.executeWork()

    addDeviceTokenF8eClient.addCalls.expectNoEvents()
  }

  test("worker does not execute on Android when no device token config") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.Android
    accountService.setActiveAccount(FullAccountMock)
    deviceTokenConfigProvider.configResult = null

    manager.executeWork()

    addDeviceTokenF8eClient.addCalls.expectNoEvents()
  }
})
