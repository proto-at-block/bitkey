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
import build.wallet.f8e.support.TicketFormFieldDTO
import build.wallet.email.Email
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
  val supportTicketF8eClient =
    SupportTicketF8eClientMock(
      TicketFormDTO(0L, emptyList(), emptyList<TicketFormConditionDTO>())
    )

  val repo = SupportTicketRepositoryImpl(
    supportTicketF8eClient = supportTicketF8eClient,
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

  val subjectField =
    SupportTicketField.TextField(
      id = 2L,
      title = "Subject",
      isRequired = true,
      knownType = SupportTicketField.KnownFieldType.Subject
    )

  val descriptionField =
    SupportTicketField.TextArea(
      id = 3L,
      title = "Description",
      isRequired = true,
      knownType = SupportTicketField.KnownFieldType.Description
    )

  val w3HardwareTypeItem =
    SupportTicketField.Picker.Item(
      title = "Bitkey (with a screen)",
      value = "bitkey__with_a_screen_device"
    )

  val w1HardwareTypeItem =
    SupportTicketField.Picker.Item(
      title = "Bitkey (without a screen)",
      value = "bitkey__without_a_screen_device"
    )

  val noHardwareItem =
    SupportTicketField.Picker.Item(
      title = "I don't have a Bitkey",
      value = "i_don_t_have_a_bitkey_device"
    )

  val hardwareTypePickerField =
    SupportTicketField.Picker(
      id = 4L,
      title = "Which Bitkey device are you using?",
      isRequired = true,
      items = listOf(w3HardwareTypeItem, w1HardwareTypeItem, noHardwareItem),
      knownType = SupportTicketField.KnownFieldType.HardwareTypePicker
    )

  val hardwareSerialNumberField =
    SupportTicketField.TextField(
      id = 5L,
      title = "What is your Bitkey serial number?",
      isRequired = false,
      knownType = SupportTicketField.KnownFieldType.HardwareSerialNumber
    )

  val androidPhoneTypeItem =
    SupportTicketField.Picker.Item(
      title = "Android",
      value = "android_bitkey_phone"
    )

  val iphonePhoneTypeItem =
    SupportTicketField.Picker.Item(
      title = "iPhone",
      value = "iphone_bitkey_phone"
    )

  val otherPhoneTypeItem =
    SupportTicketField.Picker.Item(
      title = "Not sure / Other",
      value = "not_sure_bitkey_phone"
    )

  val phoneTypePickerField =
    SupportTicketField.Picker(
      id = 6L,
      title = "What phone are you using?",
      isRequired = true,
      items = listOf(iphonePhoneTypeItem, androidPhoneTypeItem, otherPhoneTypeItem),
      knownType = SupportTicketField.KnownFieldType.PhoneTypePicker
    )

  val form =
    SupportTicketForm(
      id = 0L,
      fields = listOf(hardwareTypeField),
      conditions = OptimizedSupportTicketFieldConditions(emptyMap())
    )

  fun optionalChild(field: SupportTicketField<*>) =
    SupportTicketFieldCondition.Child(
      field = field,
      isRequired = false
    )

  val newZendeskForm =
    SupportTicketForm(
      id = 1L,
      fields =
        listOf(
          subjectField,
          descriptionField,
          hardwareTypePickerField,
          phoneTypePickerField,
          hardwareSerialNumberField
        ),
      conditions =
        listOf(
          SupportTicketFieldCondition(
            parentField = hardwareTypePickerField,
            expectedValue = SupportTicketField.RawValue.Text(w3HardwareTypeItem.value),
            children = listOf(optionalChild(hardwareSerialNumberField))
          ),
          SupportTicketFieldCondition(
            parentField = hardwareTypePickerField,
            expectedValue = SupportTicketField.RawValue.Text(w1HardwareTypeItem.value),
            children = listOf(optionalChild(hardwareSerialNumberField))
          )
        ).optimize()
    )

  beforeTest {
    firmwareDeviceInfoDao.reset()
    supportTicketF8eClient.reset()
  }

  fun MutableSupportTicketData.copyFieldsFrom(data: SupportTicketData) {
    data.asMap().forEach { (field, value) ->
      @Suppress("UNCHECKED_CAST")
      this[field as SupportTicketField<Any>] = value
    }
  }

  fun customFieldOption(
    id: Long,
    item: SupportTicketField.Picker.Item,
  ) = TicketFormFieldDTO.CustomFieldOptionDTO(
    id = id,
    name = item.title,
    value = item.value
  )

  fun pairedDeviceInfo(hwRevision: String) =
    FirmwareDeviceInfo(
      version = "1.0.0",
      serial = "serial",
      swType = "app-a-dev",
      hwRevision = hwRevision,
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

  test("loadFormStructure maps picker-backed phone and hardware known types") {
    supportTicketF8eClient.ticketFormDTO =
      TicketFormDTO(
        id = 1L,
        fields =
          listOf(
            TicketFormFieldDTO(
              id = hardwareTypePickerField.id,
              type = TicketFormFieldDTO.Type.Picker,
              knownType = TicketFormFieldDTO.KnownType.HardwareType,
              required = false,
              title = hardwareTypePickerField.title,
              options =
                listOf(
                  customFieldOption(1L, w3HardwareTypeItem)
                )
            ),
            TicketFormFieldDTO(
              id = phoneTypePickerField.id,
              type = TicketFormFieldDTO.Type.Picker,
              knownType = TicketFormFieldDTO.KnownType.PhoneMakeAndModel,
              required = false,
              title = phoneTypePickerField.title,
              options =
                listOf(
                  customFieldOption(2L, androidPhoneTypeItem)
                )
            )
          ),
        conditions = emptyList()
      )

    val form = repo.loadFormStructure().value

    form?.get(SupportTicketField.KnownFieldType.HardwareTypePicker)
      .shouldBe(
        hardwareTypePickerField.copy(
          items = listOf(w3HardwareTypeItem),
          isRequired = false
        )
      )
    form?.get(SupportTicketField.KnownFieldType.PhoneTypePicker)
      .shouldBe(
        phoneTypePickerField.copy(
          items = listOf(androidPhoneTypeItem),
          isRequired = false
        )
      )
  }

  test("prefillKnownFields includes W3 hardware type for W3 device") {
    firmwareDeviceInfoDao.storedDeviceInfo = pairedDeviceInfo(hwRevision = "w3a-core-evt")

    val data = repo.prefillKnownFields(form)

    data[hardwareTypeField].shouldBe("W3")
  }

  test("prefillKnownFields includes W1 hardware type for W1 device") {
    firmwareDeviceInfoDao.storedDeviceInfo = pairedDeviceInfo(hwRevision = "w1a-dvt")

    val data = repo.prefillKnownFields(form)

    data[hardwareTypeField].shouldBe("W1")
  }

  test("prefillKnownFields includes empty hardware type when no device is paired") {
    firmwareDeviceInfoDao.storedDeviceInfo = null

    val data = repo.prefillKnownFields(form)

    data[hardwareTypeField].shouldBe("")
  }

  test("prefillKnownFields selects W3 hardware type picker option for W3 device") {
    firmwareDeviceInfoDao.storedDeviceInfo = pairedDeviceInfo(hwRevision = "w3a-core-evt")

    val data = repo.prefillKnownFields(newZendeskForm)

    data[hardwareTypePickerField].shouldBe(w3HardwareTypeItem)
  }

  test("prefillKnownFields selects W1 hardware type picker option for W1 device") {
    firmwareDeviceInfoDao.storedDeviceInfo = pairedDeviceInfo(hwRevision = "w1a-dvt")

    val data = repo.prefillKnownFields(newZendeskForm)

    data[hardwareTypePickerField].shouldBe(w1HardwareTypeItem)
  }

  test("prefillKnownFields selects Android phone type picker option on Android") {
    val data = repo.prefillKnownFields(newZendeskForm)

    data[phoneTypePickerField].shouldBe(androidPhoneTypeItem)
  }

  test("createTicket includes conditional serial field when hardware type is prefilled") {
    firmwareDeviceInfoDao.storedDeviceInfo = pairedDeviceInfo(hwRevision = "w3a-core-evt")
    val prefilledData = repo.prefillKnownFields(newZendeskForm)
    val data =
      buildSupportTicketData {
        email = Email("user@example.com")
        sendDebugData = false
        copyFieldsFrom(prefilledData)
        this[subjectField] = "Subject"
        this[descriptionField] = "Description"
      }

    repo.createTicket(newZendeskForm, data).shouldBe(Ok(Unit))

    val ticket = supportTicketF8eClient.createTicketCalls.single()
    ticket.customFieldValues[hardwareTypePickerField.id]
      .shouldBe(TicketFormFieldDTO.Value.Text(w3HardwareTypeItem.value))
    ticket.customFieldValues[phoneTypePickerField.id]
      .shouldBe(TicketFormFieldDTO.Value.Text(androidPhoneTypeItem.value))
    ticket.customFieldValues[hardwareSerialNumberField.id]
      .shouldBe(TicketFormFieldDTO.Value.Text("serial"))
  }

})
