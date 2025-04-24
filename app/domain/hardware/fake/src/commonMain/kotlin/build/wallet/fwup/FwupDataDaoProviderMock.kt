package build.wallet.fwup

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class FwupDataDaoProviderMock(
  turbine: (name: String) -> Turbine<Any>,
) : FwupDataDaoProvider {
  val fwupDataDaoMock = FwupDataDaoMock(turbine)

  override suspend fun get(): FwupDataDao {
    return fwupDataDaoMock
  }

  fun reset(testName: String) {
    fwupDataDaoMock.reset(testName)
  }
}
