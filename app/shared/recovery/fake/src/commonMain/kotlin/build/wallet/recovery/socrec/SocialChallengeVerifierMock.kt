package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SocialChallengeVerifierMock : SocialChallengeVerifier {
  var error: SocialChallengeError? = null

  override suspend fun verifyChallenge(
    account: Account,
    delegatedDecryptionKey: DelegatedDecryptionKey,
    recoveryRelationshipId: String,
    code: String,
  ): Result<Unit, SocialChallengeError> {
    return error?.let(::Err) ?: Ok(Unit)
  }

  fun clear() {
    error = null
  }
}
