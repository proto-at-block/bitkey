package build.wallet.availability

import build.wallet.f8e.F8eEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class AppFunctionalityStatusProviderMock : AppFunctionalityStatusProvider {
  var appFunctionalityStatusFlow = MutableSharedFlow<AppFunctionalityStatus>()

  override fun appFunctionalityStatus(
    f8eEnvironment: F8eEnvironment,
  ): Flow<AppFunctionalityStatus> {
    return appFunctionalityStatusFlow
  }

  fun reset() {
    appFunctionalityStatusFlow = MutableSharedFlow()
  }
}
