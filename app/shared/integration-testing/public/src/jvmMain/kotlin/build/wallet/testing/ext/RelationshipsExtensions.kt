package build.wallet.testing.ext

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.*
import build.wallet.cloud.backup.SocRecV1BackupFeatures
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.realDelay
import build.wallet.statemachine.core.test
import build.wallet.testing.AppTester
import build.wallet.withRealTimeout
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Wait for the app to sync and verify RC [relationshipId], and include it in cloud backup.
 */
suspend fun AppTester.awaitTcIsVerifiedAndBackedUp(relationshipId: String) =
  withClue("await RC is verified and backed up") {
    appUiStateMachine.test(props = Unit) {
      // Wait until RC is synced and verified
      awaitRelationships { relationships ->
        relationships.endorsedTrustedContacts.any {
          it.relationshipId == relationshipId && it.authenticationState == TrustedContactAuthenticationState.VERIFIED
        }
      }

      // Wait for the RC to be included in the cloud backup
      awaitCloudBackupRefreshed(relationshipId)
      cancelAndIgnoreRemainingEvents()
    }
  }

/**
 * Full Account creates a Recovery Contact [Invitation].
 */
suspend fun AppTester.createTcInvite(tcName: String): TrustedContactFullInvite {
  val account = getActiveFullAccount()
  val hwPop = getHardwareFactorProofOfPossession()
  val invitation = relationshipsService
    .createInvitation(
      account = account,
      trustedContactAlias = TrustedContactAlias(tcName),
      hardwareProofOfPossession = hwPop,
      roles = setOf(TrustedContactRole.SocialRecoveryContact)
    )
    .getOrThrow()
  awaitRelationships { relationships ->
    relationships.invitations.any { it.relationshipId == invitation.invitation.relationshipId }
  }
  val pakeData = relationshipsEnrollmentAuthenticationDao
    .getByRelationshipId(invitation.invitation.relationshipId)
    .getOrThrow()
    .shouldNotBeNull()
  return TrustedContactFullInvite(
    invitation.inviteCode,
    IncomingInvitation(
      invitation.invitation.relationshipId,
      invitation.invitation.code,
      pakeData.protectedCustomerEnrollmentPakeKey.publicKey,
      setOf(TrustedContactRole.SocialRecoveryContact)
    )
  )
}

data class TrustedContactFullInvite(
  val inviteCode: String,
  val invitation: IncomingInvitation,
)

suspend fun AppTester.awaitRelationships(
  timeout: Duration = 3.seconds,
  predicate: (Relationships) -> Boolean,
): Relationships {
  return withRealTimeout(timeout) {
    withClue("await for SocRec relationships matching a predicate") {
      relationshipsService.relationships
        .filterNotNull()
        .transform { relationships ->
          if (predicate(relationships)) {
            emit(relationships)
          }
        }
        .first()
    }
  }
}

fun AppTester.getSharedInviteCode(): String {
  val sharedText = lastSharedText.shouldNotBeNull()
  return """INVITE CODE:\s*(\S+)""".toRegex()
    .find(sharedText.text)
    .shouldNotBeNull()
    .groupValues[1]
}

/**
 * Waits for a Full Account cloud backup to contain the given relationship ID. Must be called
 * while an app is running.
 */
suspend fun AppTester.awaitCloudBackupRefreshed(relationshipId: String) {
  withClue("await cloud backup includes relationships $relationshipId") {
    withRealTimeout(2.seconds) {
      var backupUpdated = false
      while (isActive && !backupUpdated) {
        val backup = readCloudBackup(CloudStoreAccountFake.ProtectedCustomerFake)
          ?: break
        backupUpdated = backup.socRecDataAvailable &&
          (backup as SocRecV1BackupFeatures)
            .fullAccountFields.shouldNotBeNull()
            .socRecSealedDekMap.containsKey(relationshipId)
        realDelay(100.milliseconds)
      }
    }
  }
}
