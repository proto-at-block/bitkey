package build.wallet.configuration

import kotlinx.coroutines.flow.MutableStateFlow

class MobilePayFiatConfigServiceFake : MobilePayFiatConfigService {
  override val config = MutableStateFlow(MobilePayFiatConfig.USD)

  fun reset() {
    config.value = MobilePayFiatConfig.USD
  }
}
