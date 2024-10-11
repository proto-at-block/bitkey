package build.wallet.inheritance

import app.cash.turbine.Turbine
import build.wallet.bitkey.inheritance.InheritanceMaterialHash
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InheritanceSyncDaoFake(
  val updateCalls: Turbine<InheritanceMaterialHash>,
  var hashResult: Result<InheritanceMaterialHash?, Error> = Ok(null),
  var updateHashResult: Result<Unit, Error> = Ok(Unit),
) : InheritanceSyncDao {
  override suspend fun getSyncedInheritanceMaterialHash(): Result<InheritanceMaterialHash?, Error> {
    return hashResult
  }

  override suspend fun updateSyncedInheritanceMaterialHash(
    hash: InheritanceMaterialHash,
  ): Result<Unit, Error> {
    updateCalls.add(hash)
    return updateHashResult
  }
}
