package build.wallet.onboarding

import build.wallet.cloud.backup.csek.SealedSsek
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingKeyboxSealedSsekDaoFake : OnboardingKeyboxSealedSsekDao {
  private var sealedSsek: SealedSsek? = null

  var shouldFailToStore: Boolean = false

  override suspend fun get(): Result<SealedSsek?, Throwable> = Ok(sealedSsek)

  override suspend fun set(value: SealedSsek): Result<Unit, Throwable> {
    return if (shouldFailToStore) {
      Err(Exception())
    } else {
      sealedSsek = value
      Ok(Unit)
    }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    sealedSsek = null
    return Ok(Unit)
  }

  fun reset() {
    sealedSsek = null
    shouldFailToStore = false
  }
}
