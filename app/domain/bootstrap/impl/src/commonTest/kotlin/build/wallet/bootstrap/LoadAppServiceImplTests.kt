package build.wallet.bootstrap

import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.auth.AppAuthPublicKeysMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.OnboardingSoftwareAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.feature.FeatureFlagServiceFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class LoadAppServiceImplTests : FunSpec({
  val featureFlagService = FeatureFlagServiceFake()
  val accountService = AccountServiceFake()
  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)
  val service = LoadAppServiceImpl(
    featureFlagService,
    accountService,
    fullAccountAuthKeyRotationService
  )

  beforeTest {
    accountService.reset()
    featureFlagService.reset()
    featureFlagService.flagsInitialized.value = true
    fullAccountAuthKeyRotationService.reset()
  }

  test("app state is not returned until feature flags are initialized") {
    featureFlagService.flagsInitialized.value = false

    accountService.accountState.value = Ok(AccountStatus.NoAccount)

    val job = async {
      service.loadAppState()
    }

    // Ensure that the`loadAppState` is suspended because feature flags are not initialized yet.
    delay(50.milliseconds)
    job.isCompleted.shouldBeFalse()

    // Now that feature flags are initialized, the `loadAppState` should return the app state.
    featureFlagService.flagsInitialized.value = true
    job.await().shouldBe(AppState.NoActiveAccount)
    job.isCompleted.shouldBeTrue()
  }

  test("throws error when account status cannot be determined") {
    accountService.accountState.value =
      Err(DbQueryError(RuntimeException("Failed to load account")))

    shouldThrow<DbQueryError> {
      service.loadAppState()
    }
  }

  test("has no active or onboarding account") {
    accountService.accountState.value = Ok(AccountStatus.NoAccount)

    service.loadAppState().shouldBe(AppState.NoActiveAccount)
  }

  context("has active lite account") {
    test("HasActiveLiteAccount app state") {
      accountService.accountState.value = Ok(ActiveAccount(LiteAccountMock))

      service.loadAppState().shouldBe(AppState.HasActiveLiteAccount(LiteAccountMock))
    }
  }

  context("has lite account onboarding to full account") {
    test("LiteAccountOnboardingToFullAccount app state") {
      accountService.accountState.value = Ok(
        AccountStatus.LiteAccountUpgradingToFullAccount(
          LiteAccountMock,
          FullAccountMock
        )
      )

      service.loadAppState()
        .shouldBe(AppState.LiteAccountOnboardingToFullAccount(LiteAccountMock, FullAccountMock))
    }
  }

  context("has onboarding full account") {
    test("OnboardingFullAccount app state") {
      accountService.accountState.value = Ok(
        AccountStatus.OnboardingAccount(FullAccountMock)
      )

      service.loadAppState().shouldBe(AppState.OnboardingFullAccount(FullAccountMock))
    }
  }

  context("has onboarding lite account") {
    test("undetermined app state") {
      accountService.accountState.value = Ok(
        AccountStatus.OnboardingAccount(LiteAccountMock)
      )

      shouldThrow<IllegalStateException> {
        service.loadAppState()
      }
    }
  }

  context("has onboarding software account") {
    test("undermined app state") {
      accountService.accountState.value = Ok(
        AccountStatus.OnboardingAccount(OnboardingSoftwareAccountMock)
      )

      shouldThrow<IllegalStateException> {
        service.loadAppState()
      }
    }
  }

  context("has active full account") {
    test("no pending auth key rotation attempt") {
      accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))

      service.loadAppState().shouldBe(
        AppState.HasActiveFullAccount(
          account = FullAccountMock,
          pendingAuthKeyRotation = null
        )
      )
    }

    test("has pending auth key rotation attempt") {
      accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))

      val authKeyRotationAttempt =
        PendingAuthKeyRotationAttempt.IncompleteAttempt(AppAuthPublicKeysMock)
      fullAccountAuthKeyRotationService.pendingKeyRotationAttempt.value = authKeyRotationAttempt

      service.loadAppState().shouldBe(
        AppState.HasActiveFullAccount(
          account = FullAccountMock,
          pendingAuthKeyRotation = authKeyRotationAttempt
        )
      )
    }
  }

  test("has active software account") {
    accountService.accountState.value = Ok(ActiveAccount(SoftwareAccountMock))

    service.loadAppState().shouldBe(
      AppState.HasActiveSoftwareAccount(
        account = SoftwareAccountMock
      )
    )
  }
})
