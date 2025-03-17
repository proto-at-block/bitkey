package build.wallet.availability

import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import kotlinx.coroutines.flow.MutableStateFlow

class AppFunctionalityServiceFake : AppFunctionalityService {
  override val status = MutableStateFlow<AppFunctionalityStatus>(FullFunctionality)

  fun reset() {
    status.value = FullFunctionality
  }
}
