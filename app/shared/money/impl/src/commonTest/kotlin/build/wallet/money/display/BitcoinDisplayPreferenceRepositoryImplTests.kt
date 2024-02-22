package build.wallet.money.display

import build.wallet.coroutines.turbine.turbines
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class BitcoinDisplayPreferenceRepositoryImplTests : FunSpec({

  val bitcoinDisplayPreferenceDao = BitcoinDisplayPreferenceDaoMock(turbines::create)

  lateinit var repository: BitcoinDisplayPreferenceRepository

  beforeTest {
    bitcoinDisplayPreferenceDao.reset()
    repository =
      BitcoinDisplayPreferenceRepositoryImpl(
        bitcoinDisplayPreferenceDao = bitcoinDisplayPreferenceDao
      )
  }

  test("bitcoin display defaults to Satoshi and returns dao value") {
    runTest {
      repository.launchSync(backgroundScope)
      runCurrent()
      repository.bitcoinDisplayUnit.value.shouldBe(BitcoinDisplayUnit.Satoshi)
      bitcoinDisplayPreferenceDao.bitcoinDisplayPreferenceFlow.emit(
        BitcoinDisplayUnit.Bitcoin
      )
      repository.bitcoinDisplayUnit.value.shouldBe(BitcoinDisplayUnit.Bitcoin)
    }
  }

  test("set bitcoin display calls dao") {
    repository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Bitcoin)
    bitcoinDisplayPreferenceDao.setBitcoinDisplayPreferenceCalls.awaitItem()
      .shouldBe(BitcoinDisplayUnit.Bitcoin)
  }

  test("clear calls dao") {
    repository.clear()
    bitcoinDisplayPreferenceDao.clearCalls.awaitItem()
  }
})
