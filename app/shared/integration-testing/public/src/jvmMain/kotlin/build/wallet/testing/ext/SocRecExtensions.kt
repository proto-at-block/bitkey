package build.wallet.testing.ext

import build.wallet.bitkey.relationships.IncomingInvitation
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.cloud.backup.SocRecV1BackupFeatures
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.statemachine.core.test
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Wait for the app to sync and verify TC [relationshipId], and include it in cloud backup.
 */
suspend fun AppTester.awaitTcIsVerifiedAndBackedUp(relationshipId: String) =
  withClue("await TC is verified and backed up") {
    app.appUiStateMachine.test(props = Unit, useVirtualTime = false) {
      // Wait until TC is synced and verified
      awaitRelationships { relationships ->
        relationships.endorsedTrustedContacts.any {
          it.relationshipId == relationshipId && it.authenticationState == TrustedContactAuthenticationState.VERIFIED
        }
      }

      // Wait for the TC to be included in the cloud backup
      awaitCloudBackupRefreshed(relationshipId)
      cancelAndIgnoreRemainingEvents()
    }
  }

/**
 * Full Account creates a Trusted Contact [Invitation].
 */
suspend fun AppTester.createTcInvite(tcName: String): TrustedContactFullInvite {
  val account = getActiveFullAccount()
  val hwPop = getHardwareFactorProofOfPossession(account.keybox)
  val invitation = app.socRecRelationshipsRepository
    .createInvitation(
      account = account,
      trustedContactAlias = TrustedContactAlias(tcName),
      hardwareProofOfPossession = hwPop
    )
    .getOrThrow()
  val pakeData = app.socRecEnrollmentAuthenticationDao
    .getByRelationshipId(invitation.invitation.relationshipId)
    .getOrThrow()
    .shouldNotBeNull()
  return TrustedContactFullInvite(
    invitation.inviteCode,
    IncomingInvitation(
      invitation.invitation.relationshipId,
      invitation.invitation.code,
      pakeData.protectedCustomerEnrollmentPakeKey.publicKey
    )
  )
}

data class TrustedContactFullInvite(
  val inviteCode: String,
  val invitation: IncomingInvitation,
)

suspend fun AppTester.awaitRelationships(
  timeout: Duration = 3.seconds,
  predicate: (SocRecRelationships) -> Boolean,
): SocRecRelationships =
  withClue("await for SocRec relationships matching a predicate") {
    app.socRecRelationshipsRepository.relationships
      .filterNotNull()
      .transform { relationships ->
        if (predicate(relationships)) {
          emit(relationships)
        }
      }
      .timeout(timeout)
      .first()
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
    withTimeout(2.seconds) {
      var backupUpdated = false
      while (isActive && !backupUpdated) {
        val backup = readCloudBackup(CloudStoreAccountFake.ProtectedCustomerFake)
          ?: break
        backupUpdated = backup.socRecDataAvailable &&
          (backup as SocRecV1BackupFeatures)
            .fullAccountFields.shouldNotBeNull()
            .socRecSealedDekMap.containsKey(relationshipId)
        delay(100.milliseconds)
      }
    }
  }
}
