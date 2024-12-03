package build.wallet.availability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class F8eAuthSignatureStatusProviderFake : F8eAuthSignatureStatusProvider {
  override fun authSignatureStatus(): StateFlow<AuthSignatureStatus> {
    return MutableStateFlow(AuthSignatureStatus.Authenticated)
  }

  override suspend fun updateAuthSignatureStatus(authSignatureStatus: AuthSignatureStatus) {
    // noop
  }

  override suspend fun clear() {
    // noop
  }
}
