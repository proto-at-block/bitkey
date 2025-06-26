package build.wallet.cloud.backup.csek

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake [SsekDao] baked by in memory storage.
 */
class SsekDaoFake : SsekDao {
  private val sseks = mutableMapOf<SealedSsek, Ssek>()

  var setResult: Result<Unit, Throwable> = Ok(Unit)
  var getErrResult: Result<Ssek?, Throwable>? = null

  override suspend fun get(key: SealedSsek): Result<Ssek?, Throwable> {
    return getErrResult ?: Ok(sseks[key])
  }

  override suspend fun set(
    key: SealedSsek,
    value: Ssek,
  ): Result<Unit, Throwable> {
    sseks[key] = value
    return setResult
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    reset()
    return Ok(Unit)
  }

  fun reset() {
    setResult = Ok(Unit)
    getErrResult = null
    sseks.clear()
  }
}
