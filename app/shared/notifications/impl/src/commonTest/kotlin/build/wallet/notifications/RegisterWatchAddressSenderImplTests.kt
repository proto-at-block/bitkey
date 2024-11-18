package build.wallet.notifications

import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.f8e.notifications.AddressAndKeysetId
import build.wallet.f8e.notifications.RegisterWatchAddressF8eClientMock
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RegisterWatchAddressSenderImplTests : FunSpec({
  val registerWatchAddressF8eClient = RegisterWatchAddressF8eClientMock(turbines::create)
  val registerWatchAddressSender = RegisterWatchAddressProcessorImpl(registerWatchAddressF8eClient)

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
    registerWatchAddressF8eClient.registerReturn = Ok(Unit)

    registerWatchAddressSender.processBatch(listOf(ctx1, ctx2))
      .shouldBeErrOfType<Error>()
      .shouldBeInstanceOf<Throwable>()
  }

  test("different f8eEnvironments errors") {
    registerWatchAddressF8eClient.registerReturn = Ok(Unit)

    registerWatchAddressSender.processBatch(listOf(ctx1, ctx3))
      .shouldBeErrOfType<Error>()
      .shouldBeInstanceOf<Throwable>()
  }

  test("successful send returns true") {
    registerWatchAddressF8eClient.registerReturn = Ok(Unit)

    registerWatchAddressSender.processBatch(listOf(ctx1))
    registerWatchAddressF8eClient.registerCalls.awaitItem().shouldBe(
      listOf(AddressAndKeysetId(ctx1.address.address, ctx1.f8eSpendingKeyset.keysetId))
    )
  }

  test("failed send returns false") {
    val error = Err(NetworkError(Throwable("uh oh!")))
    registerWatchAddressF8eClient.registerReturn = error

    registerWatchAddressSender.processBatch(listOf(ctx1)).shouldBe(error)
    registerWatchAddressF8eClient.registerCalls.awaitItem().shouldBe(
      listOf(AddressAndKeysetId(ctx1.address.address, ctx1.f8eSpendingKeyset.keysetId))
    )
  }
})
