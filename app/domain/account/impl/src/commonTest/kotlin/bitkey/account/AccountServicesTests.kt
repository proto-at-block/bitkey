package bitkey.account

import build.wallet.account.AccountServiceFake
import build.wallet.account.getAccount
import build.wallet.account.getAccountOrNull
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
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
})
