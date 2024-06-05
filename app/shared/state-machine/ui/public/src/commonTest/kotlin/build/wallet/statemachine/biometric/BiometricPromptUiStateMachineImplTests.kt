package build.wallet.statemachine.biometric

import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.inappsecurity.BiometricPreferenceFake
import build.wallet.platform.biometrics.BiometricError
import build.wallet.platform.biometrics.BiometricPrompterMock
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class BiometricPromptUiStateMachineImplTests : FunSpec({
  val biometricPrompter = BiometricPrompterMock()
  val biometricPreference = BiometricPreferenceFake()
  val appSessionManager = AppSessionManagerFake()

  val biometricPromptUiStateMachine = BiometricPromptUiStateMachineImpl(
    appSessionManager = appSessionManager,
    biometricPrompter = biometricPrompter,
    biometricPreference = biometricPreference
  )

  beforeEach {
    biometricPrompter.reset()
    biometricPreference.reset()
  }

  test("auth prompt is shown when app is in foreground") {
    biometricPreference.set(true)
    biometricPromptUiStateMachine.test(Unit) {
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
    biometricPreference.set(true)
    biometricPrompter.promptError = BiometricError.AuthenticationFailed()

    biometricPromptUiStateMachine.test(Unit) {
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
    biometricPreference.set(false)
    biometricPrompter.promptError = BiometricError.AuthenticationFailed()

    biometricPromptUiStateMachine.test(Unit) {
      // showing the splash screen and skipping auth
      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<SplashBodyModel>()

      // null model once auth has been skipped
      awaitItem().shouldBeNull()
    }
  }
})
