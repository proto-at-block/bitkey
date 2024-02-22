package build.wallet.keybox

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppDataDeleterMock(
  turbine: (name: String) -> Turbine<Any>,
) : AppDataDeleter {
  val deleteCalls = turbine("delete app data calls")

  override suspend fun deleteAll(): Result<Unit, Error> {
    deleteCalls += Unit
    return Ok(Unit)
  }
}
