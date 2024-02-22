package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface CompleteDelayNotifyService {
  /**
   * Complete Delay & Notify recovery with f8e.
   *
   * @param challenge an unsigned challenged signed by app and hardware factors and verified by
   * f8e as a way to ensure that app client knows what it's doing when completing recovery.
   * @param appSignature [challenge] signed with app authentication key.
   * @param hardwareSignature [challenge] signed with hardware authentication key.
   */
  suspend fun complete(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challenge: String,
    appSignature: String,
    hardwareSignature: String,
  ): Result<Unit, NetworkingError>
}
