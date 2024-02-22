package build.wallet.availability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class F8eAuthSignatureStatusProviderImpl : F8eAuthSignatureStatusProvider {
  private val authSignatureStatusFlow = MutableStateFlow<AuthSignatureStatus>(AuthSignatureStatus.Authenticated)

  override fun authSignatureStatus(): StateFlow<AuthSignatureStatus> {
    return authSignatureStatusFlow
  }

  override suspend fun updateAuthSignatureStatus(authSignatureStatus: AuthSignatureStatus) {
    authSignatureStatusFlow.emit(authSignatureStatus)
  }
}
