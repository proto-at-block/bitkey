package build.wallet.onboarding

import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingKeyboxHwAuthPublicKeyDaoFake : OnboardingKeyboxHwAuthPublicKeyDao {
  var hwAuthPublicKey: HwAuthPublicKey? = null

  override suspend fun get(): Result<HwAuthPublicKey?, DbTransactionError> = Ok(hwAuthPublicKey)

  override suspend fun set(publicKey: HwAuthPublicKey): Result<Unit, DbTransactionError> {
    hwAuthPublicKey = publicKey
    return Ok(Unit)
  }

  override suspend fun clear() {
    hwAuthPublicKey = null
  }
}
