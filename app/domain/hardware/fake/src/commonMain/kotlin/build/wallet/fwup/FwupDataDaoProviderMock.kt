package build.wallet.fwup

import app.cash.turbine.Turbine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FwupDataDaoProviderMock(
  turbine: (name: String) -> Turbine<Any>,
) : FwupDataDaoProvider {
  val fwupDataDaoMock = FwupDataDaoMock(turbine)
  private val daoFlow = MutableStateFlow<FwupDataDao>(fwupDataDaoMock)

  override fun get(): StateFlow<FwupDataDao> = daoFlow

  fun reset(testName: String) {
    fwupDataDaoMock.reset(testName)
    daoFlow.value = fwupDataDaoMock
  }
}
