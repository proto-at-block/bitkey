package build.wallet.onboarding

import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingKeyboxSealedCsekDaoMock : OnboardingKeyboxSealedCsekDao {
  var sealedCsek: SealedCsek? = null
  var shouldFailToStore: Boolean = false

  override suspend fun get(): Result<SealedCsek?, Throwable> = Ok(sealedCsek)

  override suspend fun set(value: SealedCsek): Result<Unit, Throwable> {
    return if (shouldFailToStore) {
      Err(Exception())
    } else {
      sealedCsek = value
      Ok(Unit)
    }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    sealedCsek = null
    return Ok(Unit)
  }

  fun reset() {
    sealedCsek = null
    shouldFailToStore = false
  }
}
