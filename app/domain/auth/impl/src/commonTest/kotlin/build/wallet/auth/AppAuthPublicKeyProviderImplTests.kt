package build.wallet.auth

import bitkey.auth.AuthTokenScope
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.account.AccountStatus.OnboardingAccount
import build.wallet.bitkey.account.appRecoveryAuthKey
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.OnboardingSoftwareAccountMock
import build.wallet.db.DbQueryError
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError.NoRecoveryInProgress
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppAuthPublicKeyProviderImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val recoveryAppAuthPublicKeyProvider = RecoveryAppAuthPublicKeyProviderMock()

  val provider =
    AppAuthPublicKeyProviderImpl(
      accountService = accountService,
      recoveryAppAuthPublicKeyProvider = recoveryAppAuthPublicKeyProvider
    )

  context("Active Full Account") {
    val account = FullAccountMock
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(account))
    recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult =
      Err(NoRecoveryInProgress)

    test("Returns AppGlobalAuthPublicKey from active Full account for Global scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Global)
      appAuthPublicKey.get().shouldBe(account.keybox.activeAppKeyBundle.authKey)
    }

    test("Returns AppRecoveryAuthPublicKey from active Full account for RecoveryApp scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Recovery)
      appAuthPublicKey.get().shouldBe(account.keybox.activeAppKeyBundle.recoveryAuthKey)
    }
  }

  context("Onboarding Full Account") {
    val account = FullAccountMock
    accountService.accountState.value = Ok(OnboardingAccount(account))
    recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult =
      Err(NoRecoveryInProgress)

    test("Returns AppGlobalAuthPublicKey from onboarding Full account for Global scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Global)
      appAuthPublicKey.get().shouldBe(account.keybox.activeAppKeyBundle.authKey)
    }

    test("Returns AppRecoveryAuthPublicKey from onboarding Full account for RecoveryApp scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Recovery)
      appAuthPublicKey.get().shouldBe(account.keybox.activeAppKeyBundle.recoveryAuthKey)
    }
  }

  context("Onboarding Software Account") {
    val account = OnboardingSoftwareAccountMock
    accountService.accountState.value = Ok(OnboardingAccount(account))

    test("Returns AppGlobalAuthPublicKey from onboarding Software account for Global scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Global)
      appAuthPublicKey.get().shouldBe(account.appGlobalAuthKey)
    }

    test("Returns AppRecoveryAuthPublicKey from onboarding Software account for RecoveryApp scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Recovery)
      appAuthPublicKey.get().shouldBe(account.appRecoveryAuthKey)
    }
  }

  context("Active Lite Account") {
    val account = LiteAccountMock
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(account))
    recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult =
      Err(NoRecoveryInProgress)

    test("Returns Err from active Lite account for Global scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Global)
      appAuthPublicKey.shouldBeErrOfType<RequestGlobalScopeForLiteAccount>()
    }

    test("Returns AppRecoveryAuthPublicKey from active Lite account for RecoveryApp scope") {
      val appAuthPublicKey = provider.getKey(account.accountId, Recovery)
      appAuthPublicKey.get().shouldBe(account.recoveryAuthKey)
    }
  }

  context("Upgrading Lite Account") {
    val fullAccount = FullAccountMock
    val status = AccountStatus.LiteAccountUpgradingToFullAccount(
      liteAccount = LiteAccountMock,
      onboardingAccount = FullAccountMock
    )
    accountService.accountState.value = Ok(status)
    recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult =
      Err(NoRecoveryInProgress)

    test("Returns AppRecoveryAuthPublicKey from onboarding Full account for Recovery scope") {
      val appAuthPublicKey = provider.getKey(fullAccount.accountId, Recovery)
      appAuthPublicKey.get().shouldBe(fullAccount.keybox.activeAppKeyBundle.recoveryAuthKey)
    }

    test("Returns AppGlobalAuthPublicKey from onboarding Full account for Global scope") {
      val appAuthPublicKey = provider.getKey(fullAccount.accountId, Global)
      appAuthPublicKey.get().shouldBe(fullAccount.keybox.activeAppKeyBundle.authKey)
    }
  }

  context("Recovering Full Account") {
    val account = FullAccountMock
    val key = AppGlobalAuthPublicKeyMock
    accountService.accountState.value = Ok(AccountStatus.NoAccount)

    test("Returns key from RecoveryAppAuthPublicKeyProvider") {
      recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult = Ok(key)
      val appAuthPublicKey = provider.getKey(account.accountId, Global)
      appAuthPublicKey.get().shouldBe(key)
    }

    test("Returns error from RecoveryAppAuthPublicKeyProvider") {
      val error =
        RecoveryAppAuthPublicKeyProviderError.FailedToReadRecoveryEntity(
          DbQueryError(Throwable())
        )
      recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult = Err(error)
      val appAuthPublicKey = provider.getKey(account.accountId, Recovery)
      appAuthPublicKey.shouldBeErrOfType<FailedToReadRecoveryStatus>()
    }
  }

  test("Returns error for account ID mismatch") {
    val account = FullAccountMock
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(account))
    recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult =
      Err(NoRecoveryInProgress)
    val appAuthPublicKey = provider.getKey(FullAccountId("some other ID"), Global)
    appAuthPublicKey.shouldBeErrOfType<UnhandledError>()
  }

  test("Returns error if there is no account that is active, onboarding, or recovering") {
    accountService.accountState.value = Ok(AccountStatus.NoAccount)
    recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecoveryResult =
      Err(NoRecoveryInProgress)
    val appAuthPublicKey = provider.getKey(FullAccountId("someId"), Global)
    appAuthPublicKey.shouldBeErrOfType<AccountMissing>()
  }
})

private suspend fun AppAuthPublicKeyProviderImpl.getKey(
  accountId: AccountId,
  tokenScope: AuthTokenScope,
) = getAppAuthPublicKeyFromAccountOrRecovery(
  accountId = accountId,
  tokenScope = tokenScope
)
