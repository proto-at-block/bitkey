package build.wallet.support

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.analytics.events.PlatformInfoProviderMock
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.support.SupportTicketF8eClientMock
import build.wallet.f8e.support.TicketFormConditionDTO
import build.wallet.f8e.support.TicketFormDTO
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.firmware.SecureBootConfig
import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class SupportTicketRepositoryImplTests : FunSpec({
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()

  val repo = SupportTicketRepositoryImpl(
    supportTicketF8eClient = SupportTicketF8eClientMock(
      TicketFormDTO(0L, emptyList(), emptyList<TicketFormConditionDTO>())
    ),
    encryptedDescriptorAttachmentCryptoService =
      object : EncryptedDescriptorAttachmentCryptoService {
        override suspend fun encryptAndUploadDescriptor(
          accountId: AccountId,
          spendingKeysets: List<SpendingKeyset>,
        ): Result<String, Error> = Ok("")
      },
    accountService = AccountServiceFake(),
    logStore =
      object : LogStore {
        override fun record(entity: LogStore.Entity) = Unit

        override fun logs(
          minimumLevel: LogLevel,
          tag: String?,
        ): Flow<List<LogStore.Entity>> = emptyFlow()

        override suspend fun getCurrentLogs(
          minimumLevel: LogLevel,
          tag: String?,
        ): List<LogStore.Entity> = emptyList()

        override fun clear() = Unit
      },
    appInstallationDao = AppInstallationDaoMock(),
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    platformInfoProvider = PlatformInfoProviderMock(),
    allFeatureFlags = emptyList(),
    accountConfigService = AccountConfigServiceFake()
  )

  val hardwareTypeField =
    SupportTicketField.TextField(
      id = 1L,
      title = "Hardware Type",
      isRequired = false,
      knownType = SupportTicketField.KnownFieldType.HardwareType
    )

  val form =
    SupportTicketForm(
      id = 0L,
      fields = listOf(hardwareTypeField),
      conditions = OptimizedSupportTicketFieldConditions(emptyMap())
    )

  beforeTest {
    firmwareDeviceInfoDao.reset()
  }

  test("prefillKnownFields includes W3 hardware type for W3 device") {
    firmwareDeviceInfoDao.storedDeviceInfo =
      FirmwareDeviceInfo(
        version = "1.0.0",
        serial = "serial",
        swType = "app-a-dev",
        hwRevision = "w3a-core-evt",
        activeSlot = FirmwareSlot.A,
        batteryCharge = 80.0,
        vCell = 4000,
        avgCurrentMa = 1,
        batteryCycles = 0,
        secureBootConfig = SecureBootConfig.DEV,
        timeRetrieved = 0,
        bioMatchStats = null,
        mcuInfo = emptyList()
      )

    val data = repo.prefillKnownFields(form)

    data[hardwareTypeField].shouldBe("W3")
  }

  test("prefillKnownFields includes W1 hardware type for W1 device") {
    firmwareDeviceInfoDao.storedDeviceInfo =
      FirmwareDeviceInfo(
        version = "1.0.0",
        serial = "serial",
        swType = "app-a-dev",
        hwRevision = "w1a-dvt",
        activeSlot = FirmwareSlot.A,
        batteryCharge = 80.0,
        vCell = 4000,
        avgCurrentMa = 1,
        batteryCycles = 0,
        secureBootConfig = SecureBootConfig.DEV,
        timeRetrieved = 0,
        bioMatchStats = null,
        mcuInfo = emptyList()
      )

    val data = repo.prefillKnownFields(form)

    data[hardwareTypeField].shouldBe("W1")
  }

  test("prefillKnownFields includes empty hardware type when no device is paired") {
    firmwareDeviceInfoDao.storedDeviceInfo = null

    val data = repo.prefillKnownFields(form)

    data[hardwareTypeField].shouldBe("")
  }
})
