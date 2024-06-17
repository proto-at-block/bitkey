package build.wallet.cloud.backup.csek

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake [CsekDao] baked by in memory storage.
 */
class CsekDaoFake : CsekDao {
  private val cseks = mutableMapOf<SealedCsek, Csek>()

  var setResult: Result<Unit, Throwable> = Ok(Unit)
  var getErrResult: Result<Csek?, Throwable>? = null

  override suspend fun get(key: SealedCsek): Result<Csek?, Throwable> {
    return getErrResult ?: Ok(cseks[key])
  }

  override suspend fun set(
    key: SealedCsek,
    value: Csek,
  ): Result<Unit, Throwable> {
    cseks[key] = value
    return setResult
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    reset()
    return Ok(Unit)
  }

  fun reset() {
    setResult = Ok(Unit)
    getErrResult = null
    cseks.clear()
  }
}
