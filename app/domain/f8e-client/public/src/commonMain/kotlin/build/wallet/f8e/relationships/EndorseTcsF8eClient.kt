package build.wallet.f8e.relationships

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContactEndorsement
import build.wallet.bitkey.relationships.TrustedContactKeyCertificate
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Used to upload the endorsement key certificates for a set of Recovery Contacts.
 * If a certificate was previously uploaded for a Recovery Contact, it will be replaced with the new
 * one.
 *
 * @param endorsements [TrustedContactKeyCertificate] associated with RC's [RelationshipId]
 */
fun interface EndorseTcsF8eClient {
  suspend fun endorseTrustedContacts(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    endorsements: List<TrustedContactEndorsement>,
  ): Result<Unit, Error>
}

suspend fun EndorseTcsF8eClient.endorseTrustedContacts(
  account: FullAccount,
  endorsements: List<TrustedContactEndorsement>,
) = endorseTrustedContacts(account.accountId, account.config.f8eEnvironment, endorsements)

fun interface EndorseTrustedContactsF8eClientProvider {
  fun get(): EndorseTcsF8eClient
}
