package build.wallet.notifications

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RegisterWatchAddressQueueImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  lateinit var q: RegisterWatchAddressQueueImpl
  val one = RegisterWatchAddressContext(BitcoinAddress("1"), F8eSpendingKeysetMock, "1", Development)
  val two = RegisterWatchAddressContext(BitcoinAddress("2"), F8eSpendingKeysetMock, "2", Production)
  val three = RegisterWatchAddressContext(BitcoinAddress("3"), F8eSpendingKeysetMock, "2", Staging)

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    q = RegisterWatchAddressQueueImpl(databaseProvider)

    val database = databaseProvider.database()

    // Create fullAccount first (top level)
    database.fullAccountQueries.insertFullAccount(FullAccountIdMock)

    // Create keybox (references fullAccount)
    database.keyboxQueries.insertKeybox(
      id = KeyboxMock.localId,
      accountId = FullAccountIdMock,
      networkType = KeyboxMock.config.bitcoinNetworkType,
      fakeHardware = KeyboxMock.config.isHardwareFake,
      f8eEnvironment = KeyboxMock.config.f8eEnvironment,
      isTestAccount = KeyboxMock.config.isTestAccount,
      isUsingSocRecFakes = KeyboxMock.config.isUsingSocRecFakes,
      delayNotifyDuration = KeyboxMock.config.delayNotifyDuration,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      canUseKeyboxKeysets = true
    )

    // Create spendingKeyset (references keybox)
    database.spendingKeysetQueries.insertKeyset(
      id = F8eSpendingKeysetMock.keysetId,
      keyboxId = KeyboxMock.localId,
      serverId = FullAccountIdMock.serverId,
      appKey = AppSpendingPublicKeyMock,
      hardwareKey = HwSpendingPublicKeyMock,
      serverKey = F8eSpendingKeysetMock.spendingPublicKey,
      isActive = true
    )
  }

  test("passing negative num to take throws") {
    shouldThrow<IllegalArgumentException> {
      q.take(-1).unwrap()
    }
  }

  test("passing negative num to removeFirst throws") {
    shouldThrow<IllegalArgumentException> {
      q.removeFirst(-1).unwrap()
    }
  }

  test("pop empty is no op") {
    q.removeFirst(1).unwrap()
  }

  test("take sees appended value") {
    q.append(one).unwrap()

    q.take(1).shouldBe(Ok(listOf(one)))
  }

  test("take sees 2 appended value") {
    q.append(one).unwrap()
    q.append(two).unwrap()

    q.take(2).shouldBe(Ok(listOf(one, two)))
  }

  test("take more than size returns entire") {
    q.append(one).unwrap()

    q.take(2).shouldBe(Ok(listOf(one)))
  }

  test("pop more than size clears") {
    q.append(one).unwrap()
    q.append(two).unwrap()
    q.append(three).unwrap()

    q.removeFirst(4).unwrap()

    q.take(1).shouldBe(Ok(emptyList()))
  }
})
