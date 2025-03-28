package bitkey.demo

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class DemoModeServiceFake(
  val validCode: String = "0000",
) : DemoModeService {
  override suspend fun enable(code: String): Result<Unit, Error> {
    return if (code == validCode) {
      Ok(Unit)
    } else {
      Err(Error("invalid code"))
    }
  }

  override suspend fun disable(): Result<Unit, Error> {
    return Ok(Unit)
  }
}
