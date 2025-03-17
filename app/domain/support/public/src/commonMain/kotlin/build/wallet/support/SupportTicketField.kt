package build.wallet.support

import kotlinx.datetime.LocalDate
import kotlin.reflect.KClass

/**
 * Representation of a field in a support ticket form.
 *
 * Note: We considered it being a `data class` where the type of the field would just be a property.
 *   However, due to Kotlin's limitations around generics type inference,
 *   it'd reduce the compile-time safety of the generics.
 */
sealed interface SupportTicketField<Value : Any> {
  val id: Long
  val title: String
  val isRequired: Boolean
  val knownType: KnownFieldType<out SupportTicketField<Value>>?

  fun rawValueFrom(value: Value): RawValue

  fun validate(value: Value): Boolean

  /**
   * [RawValue] is a representation of the value that's used for conditions and sending to server.
   * We need this for [Picker], [MultiChoice] and [Date],
   * because the representation we use in-memory is different from
   * - what we need to evaluate conditions,
   * - and what we send to the backend.
   */
  sealed interface RawValue {
    data class Text(val value: String) : RawValue

    data class Bool(val value: Boolean) : RawValue

    data class MultiChoice(val values: Set<String>) : RawValue
  }

  data class TextField(
    override val id: Long,
    override val title: String,
    override val isRequired: Boolean,
    override val knownType: KnownFieldType<TextField>?,
  ) : SupportTicketField<String> {
    override fun rawValueFrom(value: String): RawValue = RawValue.Text(value)

    override fun validate(value: String): Boolean = value.isNotBlank()
  }

  data class TextArea(
    override val id: Long,
    override val title: String,
    override val isRequired: Boolean,
    override val knownType: KnownFieldType<TextArea>?,
  ) : SupportTicketField<String> {
    override fun rawValueFrom(value: String): RawValue = RawValue.Text(value)

    override fun validate(value: String): Boolean = value.isNotBlank()
  }

  data class Picker(
    override val id: Long,
    override val title: String,
    override val isRequired: Boolean,
    val items: List<Item>,
    override val knownType: KnownFieldType<Picker>?,
  ) : SupportTicketField<Picker.Item> {
    override fun rawValueFrom(value: Item): RawValue = RawValue.Text(value.value)

    override fun validate(value: Item): Boolean = value in items

    data class Item(
      val title: String,
      val value: String,
    )
  }

  data class MultiSelect(
    override val id: Long,
    override val title: String,
    override val isRequired: Boolean,
    val items: List<Item>,
    override val knownType: KnownFieldType<MultiSelect>?,
  ) : SupportTicketField<Set<MultiSelect.Item>> {
    override fun rawValueFrom(value: Set<Item>): RawValue =
      RawValue.MultiChoice(value.map { it.value }.toSet())

    override fun validate(value: Set<Item>): Boolean =
      items.containsAll(value) && value.isNotEmpty()

    data class Item(
      val title: String,
      val value: String,
    )
  }

  data class CheckBox(
    override val id: Long,
    override val title: String,
    override val isRequired: Boolean,
    override val knownType: KnownFieldType<CheckBox>?,
  ) : SupportTicketField<Boolean> {
    override fun rawValueFrom(value: Boolean): RawValue = RawValue.Bool(value)

    override fun validate(value: Boolean): Boolean = value
  }

  data class Date(
    override val id: Long,
    override val title: String,
    override val isRequired: Boolean,
    override val knownType: KnownFieldType<Date>?,
  ) : SupportTicketField<LocalDate> {
    override fun rawValueFrom(value: LocalDate): RawValue = RawValue.Text(value.toString())

    override fun validate(value: LocalDate): Boolean = true
  }

  /**
   * Some fields have special meaning and the app has information to fill them with.
   */
  sealed interface KnownFieldType<Field : SupportTicketField<*>> {
    val fieldClass: KClass<Field>

    data object Subject : KnownFieldType<TextField> {
      override val fieldClass = TextField::class
    }

    data object Description : KnownFieldType<TextArea> {
      override val fieldClass = TextArea::class
    }

    data object Country : KnownFieldType<Picker> {
      override val fieldClass = Picker::class
    }

    data object AppVersion : KnownFieldType<TextField> {
      override val fieldClass = TextField::class
    }

    data object AppInstallationID : KnownFieldType<TextField> {
      override val fieldClass = TextField::class
    }

    data object PhoneMakeAndModel : KnownFieldType<TextField> {
      override val fieldClass = TextField::class
    }

    data object SystemNameAndVersion : KnownFieldType<TextField> {
      override val fieldClass = TextField::class
    }

    data object HardwareSerialNumber : KnownFieldType<TextField> {
      override val fieldClass = TextField::class
    }

    data object HardwareFirmwareVersion : KnownFieldType<TextField> {
      override val fieldClass = TextField::class
    }
  }
}
