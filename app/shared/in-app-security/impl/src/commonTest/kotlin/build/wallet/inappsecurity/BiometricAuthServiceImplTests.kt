package build.wallet.inappsecurity

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

class BiometricAuthServiceImplTests : FunSpec({

  val scope = TestScope()
  val biometricPreference = BiometricPreferenceFake()
  val biometricAuthService = BiometricAuthServiceImpl(
    biometricPreference,
    appCoroutineScope = scope
  )

  beforeEach {
    biometricPreference.reset()
  }

  test("isBiometricAuthRequired updates with the preference value") {
    biometricAuthService.isBiometricAuthRequired().test {
      awaitItem().shouldBeFalse()

      biometricPreference.set(true)
      scope.runCurrent()

      awaitItem().shouldBeTrue()
    }
  }
})
