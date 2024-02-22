package build.wallet.notifications

import build.wallet.LoadableValue
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.KeyboxMock2
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.onboarding.AddDeviceTokenServiceMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.HttpError
import build.wallet.platform.config.DeviceTokenConfig
import build.wallet.platform.config.DeviceTokenConfigProviderMock
import build.wallet.platform.config.TouchpointPlatform
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class DeviceTokenManagerImplTests : FunSpec({

  val addDeviceTokenService = AddDeviceTokenServiceMock(turbines::create)
  val deviceTokenConfigProvider = DeviceTokenConfigProviderMock()
  val keyboxDao = KeyboxDaoMock(turbines::create)

  val manager =
    DeviceTokenManagerImpl(
      addDeviceTokenService = addDeviceTokenService,
      deviceTokenConfigProvider = deviceTokenConfigProvider,
      keyboxDao = keyboxDao
    )

  beforeTest {
    deviceTokenConfigProvider.reset()
    keyboxDao.reset()
    addDeviceTokenService.reset()
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount with active account") {
    val activeKeybox = KeyboxMock
    val token = "abcd"
    val touchpointPlatform = TouchpointPlatform.FcmTeam
    keyboxDao.activeKeybox.emit(Ok(activeKeybox))
    keyboxDao.onboardingKeybox.emit(Ok(LoadableValue.LoadedValue(null)))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform = touchpointPlatform
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenService.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenServiceMock.AddParams>()
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
    keyboxDao.onboardingKeybox.emit(Ok(LoadableValue.LoadedValue(onboardingKeybox)))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform = touchpointPlatform
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenService.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenServiceMock.AddParams>()
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
    keyboxDao.onboardingKeybox.emit(Ok(LoadableValue.LoadedValue(onboardingKeybox)))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform = touchpointPlatform
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenService.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenServiceMock.AddParams>()
    ) {
      f8eEnvironment.shouldBe(activeKeybox.config.f8eEnvironment)
      fullAccountId.shouldBe(activeKeybox.fullAccountId)
      this.token.shouldBe(token)
      this.touchpointPlatform.shouldBe(touchpointPlatform)
    }
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount with no account") {
    keyboxDao.activeKeybox.emit(Ok(null))
    keyboxDao.onboardingKeybox.emit(Ok(LoadableValue.LoadedValue(null)))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = "abcd",
        touchpointPlatform = TouchpointPlatform.FcmTeam
      )

    result.shouldBe(DeviceTokenManagerResult.Err(DeviceTokenManagerError.NoKeybox))
  }

  test("addDeviceTokenIfActiveOrOnboardingAccount network error") {
    keyboxDao.activeKeybox.emit(Ok(KeyboxMock))
    addDeviceTokenService.addResult = Err(HttpError.NetworkError(Throwable()))
    val result =
      manager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = "abcd",
        touchpointPlatform = TouchpointPlatform.FcmTeam
      )

    addDeviceTokenService.addCalls.awaitItem()

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
        f8eEnvironment = F8eEnvironment.Development,
        fullAccountId = account,
        authTokenScope = AuthTokenScope.Global
      )

    result.shouldBe(DeviceTokenManagerResult.Ok(Unit))

    with(
      addDeviceTokenService.addCalls.awaitItem()
        .shouldBeTypeOf<AddDeviceTokenServiceMock.AddParams>()
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
        f8eEnvironment = F8eEnvironment.Development,
        fullAccountId = FullAccountId("123"),
        authTokenScope = AuthTokenScope.Global
      )

    result.shouldBe(DeviceTokenManagerResult.Err(DeviceTokenManagerError.NoDeviceToken))
  }

  test("addDeviceTokenIfPresentForAccount network error") {
    addDeviceTokenService.addResult = Err(HttpError.NetworkError(Throwable()))
    deviceTokenConfigProvider.configResult =
      DeviceTokenConfig(
        deviceToken = "abcd",
        touchpointPlatform = TouchpointPlatform.FcmTeam
      )
    val result =
      manager.addDeviceTokenIfPresentForAccount(
        f8eEnvironment = F8eEnvironment.Development,
        fullAccountId = FullAccountId("123"),
        authTokenScope = AuthTokenScope.Global
      )

    addDeviceTokenService.addCalls.awaitItem()

    result
      .result
      .shouldBeErrOfType<DeviceTokenManagerError.NetworkingError>()
  }
})
