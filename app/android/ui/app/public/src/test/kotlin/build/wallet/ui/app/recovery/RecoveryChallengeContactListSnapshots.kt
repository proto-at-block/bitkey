package build.wallet.ui.app.recovery

import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactKeyCertificateFake
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeContactListBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class RecoveryChallengeContactListSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Recovery Challenge Contact List Verified Contact Found") {
    paparazzi.snapshot {
      FormScreen(
        RecoveryChallengeContactListBodyModel(
          onExit = {},
          endorsedTrustedContacts = immutableListOf(
            EndorsedTrustedContact(
              relationshipId = "1",
              trustedContactAlias = TrustedContactAlias(alias = "alias"),
              keyCertificate = TrustedContactKeyCertificateFake,
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
          onVerifyClick = {},
          verifiedBy = immutableListOf("1"),
          onContinue = {},
          onCancelRecovery = {}
        )
      )
    }
  }

  test("Recovery Challenge Contact List Verified Empty") {
    paparazzi.snapshot {
      FormScreen(
        RecoveryChallengeContactListBodyModel(
          onExit = {},
          endorsedTrustedContacts = immutableListOf(
            EndorsedTrustedContact(
              relationshipId = "1",
              trustedContactAlias = TrustedContactAlias(alias = "alias"),
              keyCertificate = TrustedContactKeyCertificateFake,
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
          onVerifyClick = {},
          verifiedBy = immutableListOf(),
          onContinue = {},
          onCancelRecovery = {}
        )
      )
    }
  }

  test("Recovery Challenge Contact List mix of verified and await verify list") {
    paparazzi.snapshot {
      FormScreen(
        RecoveryChallengeContactListBodyModel(
          onExit = {},
          endorsedTrustedContacts = immutableListOf(
            EndorsedTrustedContact(
              relationshipId = "1",
              trustedContactAlias = TrustedContactAlias(alias = "alias"),
              keyCertificate = TrustedContactKeyCertificateFake,
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            ),
            EndorsedTrustedContact(
              relationshipId = "2",
              trustedContactAlias = TrustedContactAlias(alias = "alias2"),
              keyCertificate = TrustedContactKeyCertificateFake,
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
          onVerifyClick = {},
          verifiedBy = immutableListOf("2"),
          onContinue = {},
          onCancelRecovery = {}
        )
      )
    }
  }
})
