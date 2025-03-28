package build.wallet.statemachine.biometric

import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.biometrics.BiometricError
import build.wallet.platform.biometrics.BiometricPrompterMock
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class BiometricPromptUiStateMachineImplTests : FunSpec({
  val biometricPrompter = BiometricPrompterMock()
  val appSessionManager = AppSessionManagerFake()

  val biometricPromptUiStateMachine = BiometricPromptUiStateMachineImpl(
    appSessionManager = appSessionManager,
    biometricPrompter = biometricPrompter
  )

  beforeEach {
    biometricPrompter.reset()
  }

  test("auth prompt is shown when app is in foreground") {
    biometricPromptUiStateMachine.testWithVirtualTime(BiometricPromptProps(shouldPromptForAuth = true)) {
      // showing the splash screen and authenticating
      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<SplashBodyModel>()

      // null model once auth has succeeded
      awaitItem().shouldBeNull()

      // backgrounding and foregrounding the app
      appSessionManager.appDidEnterBackground()
      appSessionManager.appDidEnterForeground()

      // showing the splash screen and authenticating
      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<SplashBodyModel>()

      // succeeding in auth
      awaitItem().shouldBeNull()
    }
  }

  test("lock screen is shown once auth has failed") {
    biometricPrompter.promptError = BiometricError.AuthenticationFailed()

    biometricPromptUiStateMachine.test(BiometricPromptProps(shouldPromptForAuth = true)) {
      // showing the splash screen and failing to auth
      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<SplashBodyModel>()

      // showing the locked state since auth has failed
      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<SplashLockModel>()
    }
  }

  test("proceeds to null value when biometric preference is disabled") {
    biometricPromptUiStateMachine.test(BiometricPromptProps(shouldPromptForAuth = false)) {
      // null model once auth has been skipped
      awaitItem().shouldBeNull()
    }
  }
})
