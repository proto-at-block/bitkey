package build.wallet.f8e.socrec

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.bitkey.socrec.StartSocialChallengeRequestTrustedContact
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface StartSocialChallengeService {
  /**
   * Creates a new social challenge.
   */
  suspend fun startChallenge(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    trustedContacts: List<StartSocialChallengeRequestTrustedContact>,
  ): Result<SocialChallenge, NetworkingError>
}
