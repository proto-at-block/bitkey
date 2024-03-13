package build.wallet.f8e.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.RecoveryRelationshipId
import build.wallet.bitkey.socrec.TrustedContactEndorsement
import build.wallet.bitkey.socrec.TrustedContactKeyCertificate
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Used to upload the endorsement key certificates for a set of trusted contacts.
 * If a certificate was previously uploaded for a trusted contact, it will be replaced with the new
 * one.
 *
 * @param endorsements [TrustedContactKeyCertificate] associated with TC's [RecoveryRelationshipId]
 */
fun interface EndorseTrustedContactsService {
  suspend fun endorseTrustedContacts(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    endorsements: List<TrustedContactEndorsement>,
  ): Result<Unit, Error>
}

suspend fun EndorseTrustedContactsService.endorseTrustedContacts(
  account: FullAccount,
  endorsements: List<TrustedContactEndorsement>,
) = endorseTrustedContacts(account.accountId, account.config.f8eEnvironment, endorsements)

fun interface EndorseTrustedContactsServiceProvider {
  suspend fun get(): EndorseTrustedContactsService
}
