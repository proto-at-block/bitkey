package bitkey.securitycenter

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.BiometricAuthService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface BiometricActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class BiometricActionFactoryImpl(
  private val biometricService: BiometricAuthService,
) : BiometricActionFactory {
  override suspend fun create(): Flow<SecurityAction> {
    return biometricService.isBiometricAuthRequired().map { BiometricAction(it) }
  }
}
