package build.wallet.availability

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@BitkeyInject(AppScope::class)
class F8eAuthSignatureStatusProviderImpl : F8eAuthSignatureStatusProvider {
  private val authSignatureStatusFlow = MutableStateFlow<AuthSignatureStatus>(AuthSignatureStatus.Authenticated)

  override fun authSignatureStatus(): StateFlow<AuthSignatureStatus> {
    return authSignatureStatusFlow
  }

  override suspend fun updateAuthSignatureStatus(authSignatureStatus: AuthSignatureStatus) {
    authSignatureStatusFlow.emit(authSignatureStatus)
  }

  override suspend fun clear() {
    authSignatureStatusFlow.emit(AuthSignatureStatus.Authenticated)
  }
}
