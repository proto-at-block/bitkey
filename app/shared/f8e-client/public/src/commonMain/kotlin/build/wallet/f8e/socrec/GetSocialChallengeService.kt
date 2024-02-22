package build.wallet.f8e.socrec

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface GetSocialChallengeService {
  /**
   * Retrieves the status of the challenge.
   */
  suspend fun getSocialChallengeStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challengeId: String,
  ): Result<SocialChallenge, NetworkingError>
}
