package build.wallet.integration.statemachine.securitycenter

import app.cash.turbine.test
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationPreferences
import bitkey.securitycenter.SecurityActionRecommendation.ENABLE_EMAIL_NOTIFICATIONS
import bitkey.securitycenter.SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Functional tests for SecurityActionsService initialization using nullable StateFlow.
 *
 * These tests verify that the race condition between notification configuration and
 * security recommendations is properly handled. The key issue being addressed:
 *
 * 1. SecurityActionsService initially emits null (not yet loaded)
 * 2. After real data flows through (from notification preferences, account status, etc.),
 *    it emits a non-null value
 * 3. Tests and UI components should wait for non-null state before relying on
 *    the recommendations/at-risk state
 *
 * Using nullable StateFlow prevents code from seeing an initial "good" state (no at-risk items)
 * before the real state arrives, avoiding intermittent failures.
 */
class SecurityActionsInitializationFunctionalTests : FunSpec({

  test("state becomes non-null after security actions are calculated") {
    val app = launchNewApp()

    // Onboard an account to trigger security actions calculation
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      shouldSetUpNotifications = true
    )

    // Wait for security actions to be initialized with real data
    withTimeout(10.seconds) {
      app.securityActionsService.securityActionsWithRecommendations
        .first { it != null }
    }

    // Verify the state is initialized (non-null)
    app.securityActionsService.securityActionsWithRecommendations.value.shouldNotBeNull()
  }

  test("notification preferences update is reflected in security recommendations after initialization") {
    val app = launchNewApp()

    val account = app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      shouldSetUpNotifications = true
    )

    // Configure notification preferences to enable email and push
    val preferences = NotificationPreferences(
      moneyMovement = setOf(NotificationChannel.Email, NotificationChannel.Push),
      productMarketing = emptySet(),
      accountSecurity = setOf(NotificationChannel.Email, NotificationChannel.Push)
    )
    app.notificationsPreferencesCachedProvider.updateNotificationsPreferences(
      accountId = account.accountId,
      preferences = preferences,
      hwFactorProofOfPossession = null
    ).getOrThrow()

    // Wait for non-null state AND the at-risk recommendations to be cleared
    withTimeout(10.seconds) {
      app.securityActionsService.securityActionsWithRecommendations
        .first { state ->
          state != null &&
            !state.atRiskRecommendations.contains(ENABLE_EMAIL_NOTIFICATIONS) &&
            !state.atRiskRecommendations.contains(ENABLE_PUSH_NOTIFICATIONS)
        }
    }

    // Verify the state is stable (doesn't change back)
    // This is the key test for the race condition fix
    app.securityActionsService.securityActionsWithRecommendations.test {
      val state = awaitItem()
      state.shouldNotBeNull()
      state.atRiskRecommendations.shouldNotContain(ENABLE_EMAIL_NOTIFICATIONS)

      // Give some time for any delayed emissions to arrive
      delay(500.milliseconds)

      // Check that no new emissions occurred that would revert the state
      expectNoEvents()
    }
  }

  test("state is populated after onboarding") {
    val app = launchNewApp()

    // Onboard and wait for real data
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    // After onboarding, state should be non-null with real data
    withTimeout(10.seconds) {
      app.securityActionsService.securityActionsWithRecommendations
        .first { it != null }
    }

    app.securityActionsService.securityActionsWithRecommendations.value.shouldNotBeNull()
  }
})
