package build.wallet.integration.statemachine.recovery.socrec

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementProps
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant

class TrustedContactManagementFunctionalTests : FunSpec({
  val appTester = launchNewApp()
  val onExitCalls = turbines.create<Unit>("onExit")

  test("no trusted contacts") {
    val account = appTester.onboardFullAccountWithFakeHardware()

    appTester.app.trustedContactManagementUiStateMachine.test(
      TrustedContactManagementProps(
        account = account,
        onExit = { onExitCalls.add(Unit) },
        socRecRelationships = SocRecRelationships.EMPTY,
        socRecActions = appTester.app.socRecRelationshipsRepository.toActions(account)
      )
    ) {
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Trusted Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
        mainContentList[0].shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .apply {
            items
              .shouldHaveSize(0)
            footerButton.shouldNotBeNull()
          }

        onBack?.invoke()
        onExitCalls.awaitItem()
      }
    }
  }

  test("trusted contacts loaded") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    val testContact =
      TrustedContact(
        recoveryRelationshipId = "test-id",
        trustedContactAlias = TrustedContactAlias("test-contact"),
        TrustedContactIdentityKey(
          AppKey.fromPublicKey(
            "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
          )
        )
      )

    appTester.app.trustedContactManagementUiStateMachine.test(
      TrustedContactManagementProps(
        account = account,
        onExit = { onExitCalls.add(Unit) },
        socRecRelationships = SocRecRelationships.EMPTY.copy(trustedContacts = listOf(testContact)),
        socRecActions = appTester.app.socRecRelationshipsRepository.toActions(account)
      )
    ) {
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Trusted Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(1)
          .toList()[0]
          .title.shouldBe("test-contact")

        onBack?.invoke()
        onExitCalls.awaitItem()
      }
    }
  }

  test("invitations loaded") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    val testInvitation =
      Invitation(
        recoveryRelationshipId = "test-id",
        trustedContactAlias = TrustedContactAlias("test-invitation"),
        token = "test-token",
        expiresAt = Instant.DISTANT_FUTURE
      )

    appTester.app.trustedContactManagementUiStateMachine.test(
      TrustedContactManagementProps(
        account = account,
        onExit = { onExitCalls.add(Unit) },
        socRecRelationships = SocRecRelationships.EMPTY.copy(invitations = listOf(testInvitation)),
        socRecActions = appTester.app.socRecRelationshipsRepository.toActions(account)
      )
    ) {
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Trusted Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(1)
          .toList()[0]
          .title.shouldBe("test-invitation")

        onBack?.invoke()
        onExitCalls.awaitItem()
      }
    }
  }

  test("Start add new trusted contact flow") {
    val account = appTester.onboardFullAccountWithFakeHardware()

    appTester.app.trustedContactManagementUiStateMachine.test(
      TrustedContactManagementProps(
        account = account,
        onExit = { onExitCalls.add(Unit) },
        socRecRelationships = SocRecRelationships.EMPTY,
        socRecActions = appTester.app.socRecRelationshipsRepository.toActions(account)
      )
    ) {
      awaitUntilScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .footerButton
          .shouldNotBeNull()
          .onClick()
      }
      awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME)
      cancelAndIgnoreRemainingEvents()
    }
  }
})
