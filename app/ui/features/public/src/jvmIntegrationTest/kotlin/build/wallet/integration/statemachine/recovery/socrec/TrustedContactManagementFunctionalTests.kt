package build.wallet.integration.statemachine.recovery.socrec

import app.cash.turbine.ReceiveTurbine
import bitkey.relationships.Relationships
import bitkey.ui.framework.test
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.recovery.socrec.add.ShareInviteBodyModel
import build.wallet.statemachine.recovery.socrec.list.full.TrustedContactsListBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteContactBodyModel
import build.wallet.statemachine.trustedcontact.remove.RemoveTrustedContactBodyModel
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.testing.ext.assertActiveHardwareType
import build.wallet.testing.ext.awaitRelationships
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testForHardwareHappyPaths
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class TrustedContactManagementFunctionalTests : FunSpec({
  testForHardwareHappyPaths("remove pending recovery contact invite") { app, coverageMode ->
    val account = app.onboardFullAccountWithFakeHardware(hardwareType = coverageMode.hardwareType)
    app.trustedContactManagementScreenPresenter.test(
      screen = TrustedContactManagementScreen(
        account = account,
        onExit = {}
      )
    ) {
      advanceThroughTrustedContactInviteScreens("Bob", coverageMode)
      cancelAndIgnoreRemainingEvents()
    }
    val inviteRelationshipId = app.awaitRelationships { current ->
      current.invitations.any { invitation ->
        invitation.trustedContactAlias.alias == "Bob"
      }
    }.invitations.first { invitation ->
      invitation.trustedContactAlias.alias == "Bob"
    }.relationshipId

    app.trustedContactManagementScreenPresenter.test(
      screen = TrustedContactManagementScreen(
        account = account,
        onExit = {}
      )
    ) { _ ->
      awaitUntilBody<TrustedContactsListBodyModel>(
        matching = {
          it.invitations.any { invitation ->
            invitation.relationshipId == inviteRelationshipId
          }
        }
      ) {
        onContactPressed(
          invitations.first { invitation ->
            invitation.relationshipId == inviteRelationshipId
          }
        )
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeTypeOf<ViewingInvitationBodyModel>()
        .secondaryButton.shouldNotBeNull()
        .onClick()

      awaitUntilBody<RemoveTrustedContactBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      advanceThroughHardwareAuth(coverageMode)

      awaitUntilBody<TrustedContactsListBodyModel>(
        matching = {
          it.invitations.none { invitation ->
            invitation.relationshipId == inviteRelationshipId
          }
        }
      ) {
        invitations.shouldBeEmpty()
        cancelAndIgnoreRemainingEvents()
      }
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
  }

  testForHardwareHappyPaths("reinvite expired recovery contact invite") { app, coverageMode ->
    val account = app.onboardFullAccountWithFakeHardware(hardwareType = coverageMode.hardwareType)
    app.trustedContactManagementScreenPresenter.test(
      screen = TrustedContactManagementScreen(
        account = account,
        onExit = {}
      )
    ) {
      advanceThroughTrustedContactInviteScreens("Carol", coverageMode)
      cancelAndIgnoreRemainingEvents()
    }
    val inviteRelationshipId = app.awaitRelationships { current ->
      current.invitations.any { invitation ->
        invitation.trustedContactAlias.alias == "Carol"
      }
    }.invitations.first { invitation ->
      invitation.trustedContactAlias.alias == "Carol"
    }.relationshipId

    val relationships = app.awaitRelationships { current ->
      current.invitations.any { invitation ->
        invitation.relationshipId == inviteRelationshipId
      }
    }
    app.relationshipsDao.setRelationships(
      relationships.expireInvitation(inviteRelationshipId)
    ).getOrThrow()
    app.awaitRelationships { current ->
      current.invitations.any { invitation ->
        invitation.relationshipId == inviteRelationshipId && invitation.isExpired(Clock.System)
      }
    }

    app.trustedContactManagementScreenPresenter.test(
      screen = TrustedContactManagementScreen(
        account = account,
        onExit = {}
      )
    ) { _ ->
      awaitUntilBody<TrustedContactsListBodyModel>(
        matching = {
          it.invitations.any { invitation ->
            invitation.relationshipId == inviteRelationshipId && invitation.isExpired(it.now)
          }
        }
      ) {
        onContactPressed(
          invitations.first { invitation ->
            invitation.relationshipId == inviteRelationshipId
          }
        )
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeTypeOf<ViewingInvitationBodyModel>()
        .primaryButton.shouldNotBeNull()
        .onClick()

      awaitUntilBody<ReinviteContactBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      advanceThroughHardwareAuth(coverageMode)

      awaitUntilBody<ShareInviteBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitUntilBody<SuccessBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      app.sharingManager.lastSharedText.value.shouldNotBeNull()
      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughHardwareAuth(
  coverageMode: HardwareCoverageMode,
) {
  awaitUntilBody<NfcBodyModel>()
  if (coverageMode == HardwareCoverageMode.W3Private) {
    awaitUntilScreenWithBody<NfcBodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> {
      onConfirm()
    }
  }
}

private fun Relationships.expireInvitation(relationshipId: String): Relationships {
  val expiredAt = Clock.System.now().minus(1.days)
  return copy(
    invitations = invitations.map { invitation ->
      if (invitation.relationshipId == relationshipId) {
        invitation.copy(expiresAt = expiredAt)
      } else {
        invitation
      }
    }
  )
}
