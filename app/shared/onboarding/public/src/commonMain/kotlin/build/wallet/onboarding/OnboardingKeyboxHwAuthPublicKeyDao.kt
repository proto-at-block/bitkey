package build.wallet.onboarding

import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result

/**
 * Dao for storing the public key used for hardware authentication to use during onboarding.
 */
interface OnboardingKeyboxHwAuthPublicKeyDao {
  suspend fun get(): Result<HwAuthPublicKey?, DbTransactionError>

  suspend fun set(publicKey: HwAuthPublicKey): Result<Unit, DbTransactionError>

  suspend fun clear()
}
