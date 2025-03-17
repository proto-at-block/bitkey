package build.wallet.inappsecurity

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@BitkeyInject(AppScope::class)
class BiometricAuthServiceImpl(
  biometricPreference: BiometricPreference,
  appCoroutineScope: CoroutineScope,
) : BiometricAuthService {
  private val isBiometricAuthRequired = MutableStateFlow(false)

  init {
    biometricPreference.isEnabled()
      .onEach { isBiometricAuthRequired.value = it }
      .launchIn(appCoroutineScope)
      .start()
  }

  override fun isBiometricAuthRequired(): StateFlow<Boolean> = isBiometricAuthRequired
}
