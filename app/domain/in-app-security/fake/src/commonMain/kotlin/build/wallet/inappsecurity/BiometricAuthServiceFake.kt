package build.wallet.inappsecurity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BiometricAuthServiceFake : BiometricAuthService {
  val isBiometricAuthRequiredFlow = MutableStateFlow(false)

  override fun isBiometricAuthRequired(): StateFlow<Boolean> {
    return isBiometricAuthRequiredFlow
  }

  fun reset() {
    isBiometricAuthRequiredFlow.value = false
  }
}
