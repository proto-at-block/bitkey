package build.wallet.recovery

import app.cash.turbine.test
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.recovery.LocalRecoveryAttemptProgress.*
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.CreatedSpendingKeys
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class RecoveryDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  val keyset = SpendingKeysetMock
  val serverRecovery = LostHardwareServerRecoveryMock
  val sealedCsek = "sealedCsek".encodeUtf8()
  val sealedSsek = "sealedSsek".encodeUtf8()
  val customerAccount = serverRecovery.fullAccountId
  val appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey
  val appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey
  val hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey
  val serverSpendingKeyset = F8eSpendingKeysetMock
  val spendingKeyset1 = SpendingKeyset(
    localId = "keyset-app-1",
    f8eSpendingKeyset =
      F8eSpendingKeyset(
        keysetId = "keyset-server-1",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-1"))
      ),
    networkType = SIGNET,
    appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-1")),
    hardwareKey =
      HwSpendingPublicKey(
        DescriptorPublicKeyMock("hw-dpub-1", fingerprint = "deadbeef")
      )
  )
  val spendingKeyset2 = SpendingKeyset(
    localId = "keyset-app-2",
    f8eSpendingKeyset =
      F8eSpendingKeyset(
        keysetId = "keyset-server-2",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-2"))
      ),
    networkType = SIGNET,
    appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-2")),
    hardwareKey =
      HwSpendingPublicKey(
        DescriptorPublicKeyMock("hw-dpub-2", fingerprint = "deadbeef")
      )
  )

  lateinit var dao: RecoveryDaoImpl

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao =
      RecoveryDaoImpl(
        databaseProvider
      )
  }

  test("setLocalRecoveryProgress: Initiated") {
    dao.activeRecovery().test {
      awaitItem().shouldBe(Ok(NoActiveRecovery))

      setProgressInitiated(
        dao,
        customerAccount,
        keyset,
        appGlobalAuthKey,
        appRecoveryAuthKey,
        hardwareAuthKey
      )

      dao.setActiveServerRecovery(serverRecovery)

      awaitItem().shouldBe(
        Ok(
          Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery(
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            serverRecovery = serverRecovery
          )
        )
      )

      setProgressGeneratedCsek(dao, sealedCsek, sealedSsek)

      setProgressRotatedAuth(dao)

      awaitItem().shouldBe(
        Ok(
          ServerIndependentRecovery.RotatedAuthKeys(
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            sealedCsek = sealedCsek,
            sealedSsek = sealedSsek
          )
        )
      )

      setProgressCreatedSpending(dao, serverSpendingKeyset)

      awaitItem().shouldBe(
        Ok(
          CreatedSpendingKeys(
            f8eSpendingKeyset = serverSpendingKeyset,
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            sealedCsek = sealedCsek,
            sealedSsek = sealedSsek
          )
        )
      )

      setProgressUploadedDescriptorBackups(dao, listOf(spendingKeyset1, spendingKeyset2))

      awaitItem().shouldBe(
        Ok(
          ServerIndependentRecovery.UploadedDescriptorBackups(
            f8eSpendingKeyset = serverSpendingKeyset,
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            sealedCsek = sealedCsek,
            sealedSsek = sealedSsek,
            keysets = listOf(spendingKeyset1, spendingKeyset2)
          )
        )
      )

      setProgressActivatedSpending(dao, serverSpendingKeyset)

      awaitItem().shouldBe(
        Ok(
          ServerIndependentRecovery.ActivatedSpendingKeys(
            f8eSpendingKeyset = serverSpendingKeyset,
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            sealedCsek = sealedCsek,
            sealedSsek = sealedSsek,
            keysets = listOf(spendingKeyset1, spendingKeyset2)
          )
        )
      )

      setProgressDdkBackedUp(dao)

      awaitItem().shouldBe(
        Ok(
          ServerIndependentRecovery.DdkBackedUp(
            f8eSpendingKeyset = serverSpendingKeyset,
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            sealedCsek = sealedCsek,
            sealedSsek = sealedSsek,
            keysets = listOf(spendingKeyset1, spendingKeyset2)
          )
        )
      )

      setProgressBackedUpToCloud(dao)

      awaitItem().shouldBe(
        Ok(
          ServerIndependentRecovery.BackedUpToCloud(
            f8eSpendingKeyset = serverSpendingKeyset,
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            keysets = listOf(spendingKeyset1, spendingKeyset2)
          )
        )
      )

      setProgressSweptFunds(dao)

      awaitItem().shouldBe(
        Ok(
          NoActiveRecovery
        )
      )
    }
  }

  test("MaybeNoLongerRecovering") {
    dao.activeRecovery().test {
      awaitItem().shouldBe(Ok(NoActiveRecovery))

      setProgressInitiated(
        dao,
        customerAccount,
        keyset,
        appGlobalAuthKey,
        appRecoveryAuthKey,
        hardwareAuthKey
      )

      dao.setActiveServerRecovery(serverRecovery)

      awaitItem().shouldBeOkOfType<Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery>()

      setProgressGeneratedCsek(dao, sealedCsek, sealedSsek)

      dao.setActiveServerRecovery(null)

      awaitItem().shouldBeOkOfType<ServerIndependentRecovery.MaybeNoLongerRecovering>()
    }
  }

  test("NoLongerRecovering") {
    dao.activeRecovery().test {
      awaitItem().shouldBe(Ok(NoActiveRecovery))

      setProgressInitiated(
        dao,
        customerAccount,
        keyset,
        appGlobalAuthKey,
        appRecoveryAuthKey,
        hardwareAuthKey
      )

      dao.setActiveServerRecovery(serverRecovery)

      awaitItem().shouldBeOkOfType<Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery>()

      dao.setActiveServerRecovery(null)

      awaitItem().shouldBeOkOfType<Recovery.NoLongerRecovering>()
    }
  }

  test("SomeoneElseIsRecovering") {
    dao.activeRecovery().test {
      awaitItem().shouldBe(Ok(NoActiveRecovery))

      dao.setActiveServerRecovery(LostHardwareServerRecoveryMock)

      awaitItem()
        .shouldBeOkOfType<Recovery.SomeoneElseIsRecovering>()
    }
  }
})

private suspend fun setProgressInitiated(
  dao: RecoveryDaoImpl,
  fullAccountId: FullAccountId,
  keyset: SpendingKeyset,
  appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
  appRecoveryAuthPublicKey: PublicKey<AppRecoveryAuthKey>,
  hwAuthPublicKey: HwAuthPublicKey,
) {
  dao.setLocalRecoveryProgress(
    CreatedPendingKeybundles(
      fullAccountId,
      appKeyBundle =
        AppKeyBundle(
          localId = "app-key-bundle",
          spendingKey = keyset.appKey,
          authKey = appGlobalAuthPublicKey,
          networkType = BITCOIN,
          recoveryAuthKey = appRecoveryAuthPublicKey
        ),
      hwKeyBundle =
        HwKeyBundle(
          localId = "hw-key-bundle",
          spendingKey = keyset.hardwareKey,
          authKey = hwAuthPublicKey,
          networkType = BITCOIN
        ),
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      Hardware
    )
  )
}

private suspend fun setProgressGeneratedCsek(
  dao: RecoveryDaoImpl,
  sealedCsek: SealedCsek,
  sealedSsek: SealedSsek,
) {
  dao.setLocalRecoveryProgress(AttemptingCompletion(sealedCsek, sealedSsek))
}

private suspend fun setProgressRotatedAuth(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    RotatedAuthKeys
  )
}

private suspend fun setProgressCreatedSpending(
  dao: RecoveryDaoImpl,
  serverSpendingKeyset: F8eSpendingKeyset,
) {
  dao.setLocalRecoveryProgress(
    CreatedSpendingKeys(serverSpendingKeyset)
  )
}

private suspend fun setProgressActivatedSpending(
  dao: RecoveryDaoImpl,
  serverSpendingKeyset: F8eSpendingKeyset,
) {
  dao.setLocalRecoveryProgress(
    ActivatedSpendingKeys(serverSpendingKeyset)
  )
}

private suspend fun setProgressBackedUpToCloud(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    BackedUpToCloud
  )
}

private suspend fun setProgressSweptFunds(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    SweptFunds(KeyboxMock)
  )
}

private suspend fun setProgressUploadedDescriptorBackups(
  dao: RecoveryDaoImpl,
  keysets: List<SpendingKeyset>,
) {
  dao.setLocalRecoveryProgress(
    UploadedDescriptorBackups(keysets)
  )
}

private suspend fun setProgressDdkBackedUp(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    DdkBackedUp
  )
}
