package build.wallet.notifications

import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.f8e.notifications.AddressAndKeysetId
import build.wallet.f8e.notifications.RegisterWatchAddressServiceMock
import build.wallet.ktor.result.HttpError.NetworkError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class RegisterWatchAddressSenderImplTests : FunSpec({
  val registerWatchAddressServiceMock = RegisterWatchAddressServiceMock(turbines::create)
  val registerWatchAddressSender = RegisterWatchAddressSenderImpl(registerWatchAddressServiceMock)

  val ctx1 =
    RegisterWatchAddressContext(
      someBitcoinAddress,
      F8eSpendingKeysetMock,
      "123",
      Development
    )

  val ctx2 = ctx1.copy(accountId = "321")

  val ctx3 = ctx1.copy(f8eEnvironment = Staging)

  test("empty batch no work") {
    registerWatchAddressSender.processBatch(emptyList())
  }

  test("different accountIDs errors") {
    registerWatchAddressServiceMock.registerReturn = Ok(Unit)

    registerWatchAddressSender.processBatch(listOf(ctx1, ctx2))
      .shouldBeTypeOf<Err<*>>()
      .error.shouldBeInstanceOf<Throwable>()
  }

  test("different f8eEnvironments errors") {
    registerWatchAddressServiceMock.registerReturn = Ok(Unit)

    registerWatchAddressSender.processBatch(listOf(ctx1, ctx3))
      .shouldBeTypeOf<Err<*>>()
      .error.shouldBeInstanceOf<Throwable>()
  }

  test("successful send returns true") {
    registerWatchAddressServiceMock.registerReturn = Ok(Unit)

    registerWatchAddressSender.processBatch(listOf(ctx1))
    registerWatchAddressServiceMock.registerCalls.awaitItem().shouldBe(
      listOf(AddressAndKeysetId(ctx1.address.address, ctx1.f8eSpendingKeyset.keysetId))
    )
  }

  test("failed send returns false") {
    val error = Err(NetworkError(Throwable("uh oh!")))
    registerWatchAddressServiceMock.registerReturn = error

    registerWatchAddressSender.processBatch(listOf(ctx1)).shouldBe(error)
    registerWatchAddressServiceMock.registerCalls.awaitItem().shouldBe(
      listOf(AddressAndKeysetId(ctx1.address.address, ctx1.f8eSpendingKeyset.keysetId))
    )
  }
})
