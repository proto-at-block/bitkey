package build.wallet.securityrecommendations

import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.statemachine.core.Icon
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SecurityActionsServiceImplTest :
  DescribeSpec({
    val incompleteRecoveryActions = listOf(
      FakeAction(
        title = "Add a recovery contact",
        isActionCompleted = false,
        icon = Icon.SmallIconRecovery,
        actionScreenId = NavigationScreenId.NAVIGATION_SCREEN_ID_MONEY_HOME,
        category = SecurityActionCategory.RECOVERY
      ),
      FakeAction(
        title = "Add a beneficiary",
        isActionCompleted = false,
        icon = Icon.SmallIconInheritance,
        actionScreenId = NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE,
        category = SecurityActionCategory.RECOVERY
      )
    )
    val incompleteAccessActions = listOf(
      FakeAction(
        title = "Set up biometric sign-in",
        isActionCompleted = false,
        icon = Icon.SmallIconX,
        actionScreenId = NavigationScreenId.NAVIGATION_SCREEN_ID_SETTINGS,
        category = SecurityActionCategory.ACCESS
      )
    )
    val completedRecoveryActions = listOf(
      FakeAction(
        title = "Cloud Backup",
        statusInfo = "Last checked: Jan 17,2025",
        isActionCompleted = true,
        icon = Icon.SmallIconCloud,
        actionScreenId = NavigationScreenId.NAVIGATION_SCREEN_ID_SETTINGS,
        category = SecurityActionCategory.RECOVERY
      ),
      FakeAction(
        title = "Critical alerts",
        statusInfo = "Email, SMS, and push enabled",
        isActionCompleted = true,
        icon = Icon.SmallIconEmail,
        actionScreenId = NavigationScreenId.NAVIGATION_SCREEN_ID_UNSPECIFIED,
        category = SecurityActionCategory.RECOVERY
      )
    )
    val completedAccessActions = listOf(
      FakeAction(
        title = "Fingerprints",
        statusInfo = "Last updated: Jan 17,2025",
        isActionCompleted = true,
        icon = Icon.SmallIconKey,
        actionScreenId = NavigationScreenId.NAVIGATION_SCREEN_ID_UNSPECIFIED,
        category = SecurityActionCategory.ACCESS
      )
    )

    val securityActionsService = SecurityActionsServiceImpl(
      incompleteRecoveryActions +
        incompleteAccessActions +
        completedRecoveryActions +
        completedAccessActions
    )

    describe("getRecommendedActions") {
      context("when fetching incomplete actions") {
        it("returns the incomplete actions") {
          val recommendedActions = securityActionsService.getRecommendedActions(false)
          recommendedActions.size shouldBe 2
          recommendedActions[SecurityActionCategory.ACCESS] shouldBe incompleteAccessActions
          recommendedActions[SecurityActionCategory.RECOVERY] shouldBe incompleteRecoveryActions
        }
      }
      context("when fetching complete actions") {
        it("returns the complete actions") {
          val recommendedActions = securityActionsService.getRecommendedActions(true)
          recommendedActions.size shouldBe 2
          recommendedActions[SecurityActionCategory.ACCESS] shouldBe completedAccessActions
          recommendedActions[SecurityActionCategory.RECOVERY] shouldBe completedRecoveryActions
        }
      }
    }
  })
