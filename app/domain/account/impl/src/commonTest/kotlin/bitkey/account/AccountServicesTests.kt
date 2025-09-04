package bitkey.account

import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.LiteAccountUpgradingToFullAccount
import build.wallet.account.getAccount
import build.wallet.account.getAccountOrNull
import build.wallet.account.getActiveOrOnboardingAccountOrNull
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec

class AccountServicesTests : FunSpec({
  test("getAccountOrNull - no account") {
    val service = AccountServiceFake()
    service.getAccountOrNull<FullAccount>().shouldBeOk(null)
    service.getAccountOrNull<LiteAccount>().shouldBeOk(null)
    service.getAccountOrNull<SoftwareAccount>().shouldBeOk(null)
  }

  test("getAccountOrNull - found account of different type") {
    val service = AccountServiceFake()
    service.setActiveAccount(FullAccountMock)
    service.getAccountOrNull<LiteAccount>().shouldBeErr(
      Error("No active LiteAccount present, found FullAccount.")
    )
  }

  test("getAccountOrNull - found account of type") {
    val service = AccountServiceFake()
    service.setActiveAccount(FullAccountMock)
    service.getAccountOrNull<FullAccount>().shouldBeOk(FullAccountMock)
  }

  test("getAccount - no account") {
    val service = AccountServiceFake()
    service.getAccount<FullAccount>().shouldBeErr(
      Error("No active FullAccount present, found none.")
    )
    service.getAccount<LiteAccount>().shouldBeErr(
      Error("No active LiteAccount present, found none.")
    )
    service.getAccount<SoftwareAccount>().shouldBeErr(
      Error("No active SoftwareAccount present, found none.")
    )
  }

  test("getAccount - found account of different type") {
    val service = AccountServiceFake()
    service.setActiveAccount(FullAccountMock)
    service.getAccount<LiteAccount>().shouldBeErr(
      Error("No active LiteAccount present, found FullAccount.")
    )
  }

  test("getAccount - found account of type") {
    val service = AccountServiceFake()
    service.setActiveAccount(FullAccountMock)
    service.getAccount<FullAccount>().shouldBeOk(FullAccountMock)
  }

  test("getActiveOrOnboardingAccountOrNull - no accounts") {
    val service = AccountServiceFake()
    service.getActiveOrOnboardingAccountOrNull<FullAccount>().shouldBeOk(null)
    service.getActiveOrOnboardingAccountOrNull<LiteAccount>().shouldBeOk(null)
    service.getActiveOrOnboardingAccountOrNull<SoftwareAccount>().shouldBeOk(null)
  }

  test("getActiveOrOnboardingAccountOrNull - active full account") {
    val service = AccountServiceFake()
    service.setActiveAccount(FullAccountMock)
    service.getActiveOrOnboardingAccountOrNull<LiteAccount>().shouldBeErr(
      Error("No active or onboarding LiteAccount present, found FullAccount.")
    )
    service.getActiveOrOnboardingAccountOrNull<FullAccount>().shouldBeOk(FullAccountMock)
  }

  test("getActiveOrOnboardingAccountOrNull - active lite account") {
    val service = AccountServiceFake()
    service.setActiveAccount(LiteAccountMock)
    service.getActiveOrOnboardingAccountOrNull<FullAccount>().shouldBeErr(
      Error("No active or onboarding FullAccount present, found LiteAccount.")
    )
    service.getActiveOrOnboardingAccountOrNull<LiteAccount>().shouldBeOk(LiteAccountMock)
  }

  test("getActiveOrOnboardingAccountOrNull - onboarding full account") {
    val service = AccountServiceFake()
    service.saveAccountAndBeginOnboarding(FullAccountMock)
    service.getActiveOrOnboardingAccountOrNull<LiteAccount>().shouldBeErr(
      Error("No active or onboarding LiteAccount present, found FullAccount.")
    )
    service.getActiveOrOnboardingAccountOrNull<FullAccount>().shouldBeOk(FullAccountMock)
  }

  test("getActiveOrOnboardingAccountOrNull - onboarding lite account") {
    val service = AccountServiceFake()
    service.saveAccountAndBeginOnboarding(LiteAccountMock)
    service.getActiveOrOnboardingAccountOrNull<FullAccount>().shouldBeErr(
      Error("No active or onboarding FullAccount present, found LiteAccount.")
    )
    service.getActiveOrOnboardingAccountOrNull<LiteAccount>().shouldBeOk(LiteAccountMock)
  }

  test("getActiveOrOnboardingAccountOrNull - upgrading to full account") {
    val service = AccountServiceFake()

    service.accountState.value = Ok(
      LiteAccountUpgradingToFullAccount(
        liteAccount = LiteAccountMock,
        onboardingAccount = FullAccountMock
      )
    )

    service.getActiveOrOnboardingAccountOrNull<FullAccount>().shouldBeOk(FullAccountMock)
    service.getActiveOrOnboardingAccountOrNull<LiteAccount>().shouldBeErr(
      Error("No active or onboarding LiteAccount present, found FullAccount.")
    )
  }
})
