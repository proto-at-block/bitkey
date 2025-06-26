package build.wallet.testing.ext

import app.cash.turbine.test
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.encrypt.toSecp256k1PublicKey
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.testing.AppTester
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.matchers.shouldBe

suspend fun AppTester.awaitNoActiveRecovery() {
  recoveryStatusService.status().test {
    awaitUntil { it.get() is NoActiveRecovery }
  }
}

suspend fun AppTester.createLostHardwareKeyset(account: FullAccount): SpendingKeyset {
  // Since we use mock hardware, a keyset that we've lost hardware for is equivalent to
  // a keyset that we've deleted the private keys for
  val newKeyBundle = appKeysGenerator.generateKeyBundle().getOrThrow()
  newKeyBundle.networkType.shouldBe(initialBitcoinNetworkType)
  appPrivateKeyDao.remove(newKeyBundle.authKey)
  appPrivateKeyDao.remove(newKeyBundle.spendingKey)

  val hwKeyBundle =
    HwKeyBundle(
      localId = "fake-lost-hardware-key-bundle-id",
      spendingKey = HwSpendingPublicKey(newKeyBundle.spendingKey.key),
      authKey = HwAuthPublicKey(newKeyBundle.authKey.toSecp256k1PublicKey()),
      networkType = newKeyBundle.networkType
    )

  val appKeyBundle = appKeysGenerator.generateKeyBundle().getOrThrow()
  appKeyBundle.networkType.shouldBe(initialBitcoinNetworkType)

  val f8eSpendingKeyset = createAccountKeysetF8eClient
    .createKeyset(
      f8eEnvironment = account.config.f8eEnvironment,
      fullAccountId = account.accountId,
      hardwareSpendingKey = HwSpendingPublicKey(hwKeyBundle.spendingKey.key),
      appSpendingKey = appKeyBundle.spendingKey,
      network = appKeyBundle.networkType,
      appAuthKey = account.keybox.activeAppKeyBundle.authKey,
      hardwareProofOfPossession = getHardwareFactorProofOfPossession()
    )
    .getOrThrow()

  val keyset = SpendingKeyset(
    localId = "fake-spending-keyset-id",
    appKey = appKeyBundle.spendingKey,
    networkType = appKeyBundle.networkType,
    hardwareKey = hwKeyBundle.spendingKey,
    f8eSpendingKeyset = f8eSpendingKeyset
  )

  keyboxDao.saveKeyboxAsActive(account.keybox).getOrThrow()
  return keyset
}
