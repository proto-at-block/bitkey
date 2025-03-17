package build.wallet.recovery

import app.cash.turbine.test
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.recovery.LocalRecoveryAttemptProgress.AttemptingCompletion
import build.wallet.recovery.LocalRecoveryAttemptProgress.BackedUpToCloud
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import build.wallet.recovery.LocalRecoveryAttemptProgress.RotatedAuthKeys
import build.wallet.recovery.LocalRecoveryAttemptProgress.RotatedSpendingKeys
import build.wallet.recovery.LocalRecoveryAttemptProgress.SweptFunds
import build.wallet.recovery.Recovery.NoActiveRecovery
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
  val customerAccount = serverRecovery.fullAccountId
  val appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey
  val appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey
  val hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey
  val serverSpendingKeyset = F8eSpendingKeysetMock

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

      setProgressGeneratedCsek(dao, sealedCsek)

      setProgressRotatedAuth(dao)

      awaitItem().shouldBe(
        Ok(
          Recovery.StillRecovering.ServerIndependentRecovery.RotatedAuthKeys(
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware,
            sealedCsek = sealedCsek
          )
        )
      )

      setProgressRotatedSpending(dao, serverSpendingKeyset)

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
            sealedCsek = sealedCsek
          )
        )
      )

      setProgressBackedUpToCloud(dao)

      awaitItem().shouldBe(
        Ok(
          Recovery.StillRecovering.ServerIndependentRecovery.BackedUpToCloud(
            f8eSpendingKeyset = serverSpendingKeyset,
            fullAccountId = serverRecovery.fullAccountId,
            appSpendingKey = keyset.appKey,
            appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
            appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
            hardwareSpendingKey = keyset.hardwareKey,
            hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            factorToRecover = Hardware
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

      setProgressGeneratedCsek(dao, sealedCsek)

      dao.setActiveServerRecovery(null)

      awaitItem().shouldBeOkOfType<Recovery.StillRecovering.ServerIndependentRecovery.MaybeNoLongerRecovering>()
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
) {
  dao.setLocalRecoveryProgress(AttemptingCompletion(sealedCsek))
}

private suspend fun setProgressRotatedAuth(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    RotatedAuthKeys
  )
}

private suspend fun setProgressRotatedSpending(
  dao: RecoveryDaoImpl,
  serverSpendingKeyset: F8eSpendingKeyset,
) {
  dao.setLocalRecoveryProgress(
    RotatedSpendingKeys(serverSpendingKeyset)
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
