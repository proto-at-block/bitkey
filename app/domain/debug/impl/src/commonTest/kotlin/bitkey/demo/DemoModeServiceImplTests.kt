package bitkey.demo

import bitkey.account.AccountConfigServiceFake
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.demo.DemoModeF8eClient
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.nfc.FakeHardwareKeyStoreFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DemoModeServiceImplTests : FunSpec({
  val accountConfigService = AccountConfigServiceFake()
  val demoModeF8eClient = DemoModeF8eClientFake()
  val w1FakeHardwareKeyStore = FakeHardwareKeyStoreFake()
  val w3FakeHardwareKeyStore = FakeHardwareKeyStoreFake()

  val service = DemoModeServiceImpl(
    accountConfigService = accountConfigService,
    demoModeF8eClient = demoModeF8eClient,
    w1FakeHardwareKeyStore = w1FakeHardwareKeyStore,
    w3FakeHardwareKeyStore = w3FakeHardwareKeyStore
  )

  beforeTest {
    accountConfigService.reset()
    demoModeF8eClient.reset()
    w1FakeHardwareKeyStore.clear()
    w3FakeHardwareKeyStore.clear()
  }

  test("enable clears W1 and W3 fake hardware key stores") {
    val w1SeedBefore = w1FakeHardwareKeyStore.getSeed()
    val w3SeedBefore = w3FakeHardwareKeyStore.getSeed()

    service.enable("1234").shouldBeOk()

    w1FakeHardwareKeyStore.getSeed().shouldNotBe(w1SeedBefore)
    w3FakeHardwareKeyStore.getSeed().shouldNotBe(w3SeedBefore)
  }

  test("enable does not clear fake hardware key stores when demo code is rejected") {
    demoModeF8eClient.result = Err(Error("invalid code"))
    val w1SeedBefore = w1FakeHardwareKeyStore.getSeed()
    val w3SeedBefore = w3FakeHardwareKeyStore.getSeed()

    service.enable("0000").shouldBeErrOfType<Error>()

    w1FakeHardwareKeyStore.getSeed().shouldBe(w1SeedBefore)
    w3FakeHardwareKeyStore.getSeed().shouldBe(w3SeedBefore)
  }

  test("disable does not clear fake hardware key stores") {
    val w1SeedBefore = w1FakeHardwareKeyStore.getSeed()
    val w3SeedBefore = w3FakeHardwareKeyStore.getSeed()

    service.disable().shouldBeOk()

    w1FakeHardwareKeyStore.getSeed().shouldBe(w1SeedBefore)
    w3FakeHardwareKeyStore.getSeed().shouldBe(w3SeedBefore)
  }
})

private class DemoModeF8eClientFake : DemoModeF8eClient {
  var result: Result<EmptyResponseBody, Error> = Ok(EmptyResponseBody)

  override suspend fun initiateDemoMode(
    f8eEnvironment: F8eEnvironment,
    code: String,
  ): Result<EmptyResponseBody, Error> = result

  fun reset() {
    result = Ok(EmptyResponseBody)
  }
}
