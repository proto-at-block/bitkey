package build.wallet.support

import build.wallet.email.EmailValidatorMock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.random.nextInt

class SupportTicketFormValidatorImplTests : DescribeSpec({

  val emailValidator = EmailValidatorMock()
  val validator = SupportTicketFormValidatorImpl(emailValidator)

  beforeTest {
    emailValidator.reset()
    // Most of our tests require email to be valid, so we default to that
    emailValidator.isValid = true
  }

  context("A form with no fields") {
    val form = simpleForm()

    describe("validate") {
      it("fails with invalid email") {
        emailValidator.isValid = false
        val data = buildSupportTicketData { }

        validator.validate(form, data).shouldBeFalse()
      }

      it("succeeds with valid email") {
        emailValidator.isValid = true
        val data = buildSupportTicketData { }

        validator.validate(form, data).shouldBeTrue()
      }

      it("succeeds with valid email and any debug data setting") {
        Arb.boolean().checkAll { debugDataChecked ->
          emailValidator.isValid = true
          val data =
            buildSupportTicketData {
              sendDebugData = debugDataChecked
            }

          validator.validate(form, data).shouldBeTrue()
        }
      }
    }
  }

  context("A form with one field") {
    context("required text field") {
      val form = simpleForm(TestValues.requiredTextField)

      describe("validate") {
        it("fails with blank or missing value") {
          Arb.blankOrMissingText.checkAll { value ->
            val data =
              buildSupportTicketData {
                if (value != null) {
                  this[TestValues.requiredTextField] = value
                }
              }
            validator.validate(form, data).shouldBeFalse()
          }
        }
        it("succeeds with non-blank value") {
          Arb.validText.checkAll { value ->
            val data =
              buildSupportTicketData {
                this[TestValues.requiredTextField] = value
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("optional text field") {
      val form = simpleForm(TestValues.optionalTextField)

      describe("validate") {
        it("succeeds with any value") {
          Arb.anyText.checkAll { value ->
            val data =
              buildSupportTicketData {
                if (value != null) {
                  this[TestValues.optionalTextField] = value
                }
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("required text area") {
      val form = simpleForm(TestValues.requiredTextArea)

      describe("validate") {
        it("fails with blank or missing value") {
          Arb.blankOrMissingText.checkAll { value ->
            val data =
              buildSupportTicketData {
                if (value != null) {
                  this[TestValues.requiredTextArea] = value
                }
              }
            validator.validate(form, data).shouldBeFalse()
          }
        }
        it("succeeds with non-blank value") {
          Arb.validText.checkAll { value ->
            val data =
              buildSupportTicketData {
                this[TestValues.requiredTextArea] = value
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("optional text area") {
      val form = simpleForm(TestValues.optionalTextArea)

      describe("validate") {
        it("succeeds with any value") {
          Arb.anyText.checkAll { value ->
            val data =
              buildSupportTicketData {
                if (value != null) {
                  this[TestValues.optionalTextArea] = value
                }
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("required checkbox") {
      val form = simpleForm(TestValues.requiredCheckBox)

      describe("validate") {
        it("fails with missing value") {
          val data = buildSupportTicketData { }
          validator.validate(form, data).shouldBeFalse()
        }
        it("fails with false value") {
          val data =
            buildSupportTicketData {
              this[TestValues.requiredCheckBox] = false
            }
          validator.validate(form, data).shouldBeFalse()
        }
        it("succeeds with true value") {
          val data =
            buildSupportTicketData {
              this[TestValues.requiredCheckBox] = true
            }
          validator.validate(form, data).shouldBeTrue()
        }
      }
    }

    context("optional checkbox") {
      val form = simpleForm(TestValues.optionalCheckBox)

      describe("validate") {
        it("succeeds with missing value") {
          val data = buildSupportTicketData { }
          validator.validate(form, data).shouldBeTrue()
        }
        it("succeeds with false value") {
          val data =
            buildSupportTicketData {
              this[TestValues.optionalCheckBox] = false
            }
          validator.validate(form, data).shouldBeTrue()
        }
        it("succeeds with true value") {
          val data =
            buildSupportTicketData {
              this[TestValues.optionalCheckBox] = true
            }
          validator.validate(form, data).shouldBeTrue()
        }
      }
    }

    context("required date") {
      val form = simpleForm(TestValues.requiredDate)

      describe("validate") {
        it("fails with missing value") {
          val data = buildSupportTicketData { }
          validator.validate(form, data).shouldBeFalse()
        }
        it("succeeds with any date") {
          Arb.date().checkAll { date ->
            val data =
              buildSupportTicketData {
                this[TestValues.requiredDate] = date
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("optional date") {
      val form = simpleForm(TestValues.optionalDate)

      describe("validate") {
        it("succeeds with missing value") {
          val data = buildSupportTicketData { }
          validator.validate(form, data).shouldBeTrue()
        }
        it("succeeds with any date") {
          Arb.date().checkAll { date ->
            val data =
              buildSupportTicketData {
                this[TestValues.optionalDate] = date
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("required multiselect") {
      val form = simpleForm(TestValues.requiredMultiSelect)

      describe("validate") {
        it("fails with missing value") {
          val data = buildSupportTicketData {}
          validator.validate(form, data).shouldBeFalse()
        }
        it("fails with empty value") {
          val data =
            buildSupportTicketData {
              this[TestValues.requiredMultiSelect] = emptySet()
            }
          validator.validate(form, data).shouldBeFalse()
        }
        describe("succeeds with any item") {
          withData(
            setOf(TestValues.requiredMultiSelectItem1),
            setOf(TestValues.requiredMultiSelectItem2),
            setOf(TestValues.requiredMultiSelectItem1, TestValues.requiredMultiSelectItem2)
          ) { items ->
            val data =
              buildSupportTicketData {
                this[TestValues.requiredMultiSelect] = items
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("optional multiselect") {
      val form = simpleForm(TestValues.optionalMultiSelect)

      describe("validate") {
        it("succeeds with missing value") {
          val data = buildSupportTicketData {}
          validator.validate(form, data).shouldBeTrue()
        }
        describe("succeeds with any or no items") {
          withData(
            setOf(),
            setOf(TestValues.optionalMultiSelectItem1),
            setOf(TestValues.optionalMultiSelectItem2),
            setOf(TestValues.optionalMultiSelectItem1, TestValues.optionalMultiSelectItem2)
          ) { items ->
            val data =
              buildSupportTicketData {
                this[TestValues.optionalMultiSelect] = items
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("required picker") {
      val form = simpleForm(TestValues.requiredPicker)

      describe("validate") {
        it("fails with missing value") {
          val data = buildSupportTicketData {}
          validator.validate(form, data).shouldBeFalse()
        }
        describe("succeeds with any item") {
          withData(
            TestValues.requiredPickerItem1,
            TestValues.requiredPickerItem2
          ) { item ->
            val data =
              buildSupportTicketData {
                this[TestValues.requiredPicker] = item
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }

    context("optional picker") {
      val form = simpleForm(TestValues.optionalPicker)

      describe("validate") {
        it("succeeds with missing value") {
          val data = buildSupportTicketData {}
          validator.validate(form, data).shouldBeTrue()
        }
        describe("succeeds with any item") {
          withData(
            TestValues.optionalPickerItem1,
            TestValues.optionalPickerItem2
          ) { item ->
            val data =
              buildSupportTicketData {
                this[TestValues.optionalPicker] = item
              }
            validator.validate(form, data).shouldBeTrue()
          }
        }
      }
    }
  }

  context("A form with one required and one optional text field") {
    val form =
      simpleForm(
        TestValues.requiredTextField,
        TestValues.optionalTextField
      )

    describe("validate") {
      it("fails on empty data with invalid email") {
        emailValidator.isValid = false
        val data = buildSupportTicketData { }
        validator.validate(form, data).shouldBeFalse()
      }

      it("fails with blank or missing required field value") {
        Arb.blankOrMissingText.checkAll { value ->
          val data =
            buildSupportTicketData {
              if (value != null) {
                this[TestValues.requiredTextField] = value
              }
            }

          validator.validate(form, data).shouldBeFalse()
        }
      }

      it("fails with any optional field value") {
        Arb.anyText.checkAll { value ->
          val data =
            buildSupportTicketData {
              if (value != null) {
                this[TestValues.optionalTextField] = value
              }
            }

          validator.validate(form, data).shouldBeFalse()
        }
      }

      it("fails with valid required field value and invalid email") {
        emailValidator.isValid = false
        Arb.validText.checkAll { value ->
          val data =
            buildSupportTicketData {
              this[TestValues.requiredTextField] = value
            }

          validator.validate(form, data).shouldBeFalse()
        }
      }

      it("succeeds with a valid required field value") {
        Arb.validText.checkAll { value ->
          val data =
            buildSupportTicketData {
              this[TestValues.requiredTextField] = value
            }

          validator.validate(form, data).shouldBeTrue()
        }
      }

      it("succeeds with a valid required field value and any optional field value") {
        checkAll(Arb.validText, Arb.anyText) { requiredFieldValue, optionalFieldValue ->
          val data =
            buildSupportTicketData {
              this[TestValues.requiredTextField] = requiredFieldValue
              if (optionalFieldValue != null) {
                this[TestValues.optionalTextField] = optionalFieldValue
              }
            }

          validator.validate(form, data).shouldBeTrue()
        }
      }
    }
  }

  context("A form with an optional picker, one required and one optional text field") {
    context("Item 1 makes required field optional") {
      context("Item 2 has no condition") {
        val form =
          conditionalForm(
            fields =
              listOf(
                TestValues.optionalPicker,
                TestValues.requiredTextField,
                TestValues.optionalTextField
              ),
            conditions =
              listOf(
                SupportTicketFieldCondition(
                  parentField = TestValues.optionalPicker,
                  expectedValue =
                    TestValues.optionalPicker.rawValueFrom(
                      TestValues.optionalPickerItem1
                    ),
                  children =
                    listOf(
                      SupportTicketFieldCondition.Child(
                        field = TestValues.requiredTextField,
                        isRequired = false
                      )
                    )
                )
              )
          )

        describe("validate") {
          it("succeeds with empty data") {
            val data = buildSupportTicketData { }

            validator.validate(form, data).shouldBeTrue()
          }

          it("succeeds with item 2") {
            val data =
              buildSupportTicketData {
                this[TestValues.optionalPicker] = TestValues.optionalPickerItem2
              }

            validator.validate(form, data).shouldBeTrue()
          }

          it("succeeds with item 1 and any required field value") {
            Arb.anyText.checkAll { value ->
              val data =
                buildSupportTicketData {
                  this[TestValues.optionalPicker] = TestValues.optionalPickerItem1
                  if (value != null) {
                    this[TestValues.requiredTextField] = value
                  }
                }

              validator.validate(form, data).shouldBeTrue()
            }
          }
        }
      }

      context("Item 2 makes optional field required") {
        val form =
          conditionalForm(
            fields =
              listOf(
                TestValues.optionalPicker,
                TestValues.requiredTextField,
                TestValues.optionalTextField
              ),
            conditions =
              listOf(
                SupportTicketFieldCondition(
                  parentField = TestValues.optionalPicker,
                  expectedValue =
                    TestValues.optionalPicker.rawValueFrom(
                      TestValues.optionalPickerItem1
                    ),
                  children =
                    listOf(
                      SupportTicketFieldCondition.Child(
                        field = TestValues.requiredTextField,
                        isRequired = false
                      )
                    )
                ),
                SupportTicketFieldCondition(
                  parentField = TestValues.optionalPicker,
                  expectedValue =
                    TestValues.optionalPicker.rawValueFrom(
                      TestValues.optionalPickerItem2
                    ),
                  children =
                    listOf(
                      SupportTicketFieldCondition.Child(
                        field = TestValues.optionalTextField,
                        isRequired = true
                      )
                    )
                )
              )
          )

        describe("validate") {
          it("succeeds with empty data") {
            val data = buildSupportTicketData { }

            validator.validate(form, data).shouldBeTrue()
          }

          it("succeeds with item 1 and any required field value") {
            Arb.anyText.checkAll { value ->
              val data =
                buildSupportTicketData {
                  this[TestValues.optionalPicker] = TestValues.optionalPickerItem1
                  if (value != null) {
                    this[TestValues.requiredTextField] = value
                  }
                }

              validator.validate(form, data).shouldBeTrue()
            }
          }

          it("fails with item 2 and blank or missing optional field value") {
            Arb.blankOrMissingText.checkAll { value ->
              val data =
                buildSupportTicketData {
                  this[TestValues.optionalPicker] = TestValues.optionalPickerItem2
                  if (value != null) {
                    this[TestValues.optionalTextField] = value
                  }
                }

              validator.validate(form, data).shouldBeFalse()
            }
          }

          it("succeeds with item 2 and valid optional field value") {
            Arb.validText.checkAll { value ->
              val data =
                buildSupportTicketData {
                  this[TestValues.optionalPicker] = TestValues.optionalPickerItem2
                  this[TestValues.optionalTextField] = value
                }

              validator.validate(form, data).shouldBeTrue()
            }
          }
        }
      }
    }
  }
}) {
  private object TestValues {
    val requiredTextField =
      SupportTicketField.TextField(
        id = 1,
        title = "Required Text Field",
        isRequired = true,
        knownType = null
      )
    val optionalTextField =
      SupportTicketField.TextField(
        id = 2,
        title = "Optional Text Field",
        isRequired = false,
        knownType = null
      )
    val requiredTextArea =
      SupportTicketField.TextArea(
        id = 3,
        title = "Required Text Area",
        isRequired = true,
        knownType = null
      )
    val optionalTextArea =
      SupportTicketField.TextArea(
        id = 4,
        title = "Optional Text Area",
        isRequired = false,
        knownType = null
      )
    val requiredCheckBox =
      SupportTicketField.CheckBox(
        id = 5,
        title = "Required CheckBox",
        isRequired = true,
        knownType = null
      )
    val optionalCheckBox =
      SupportTicketField.CheckBox(
        id = 6,
        title = "Optional CheckBox",
        isRequired = false,
        knownType = null
      )
    val requiredDate =
      SupportTicketField.Date(
        id = 7,
        title = "Required Date",
        isRequired = true,
        knownType = null
      )
    val optionalDate =
      SupportTicketField.Date(
        id = 8,
        title = "Optional Date",
        isRequired = false,
        knownType = null
      )
    val requiredMultiSelectItem1 =
      SupportTicketField.MultiSelect.Item(
        title = "Required MultiSelect Item 1",
        value = "required-multiselect-item-1"
      )
    val requiredMultiSelectItem2 =
      SupportTicketField.MultiSelect.Item(
        title = "Required MultiSelect Item 2",
        value = "required-multiselect-item-2"
      )
    val requiredMultiSelect =
      SupportTicketField.MultiSelect(
        id = 9,
        title = "Required MultiSelect",
        isRequired = true,
        knownType = null,
        items = listOf(requiredMultiSelectItem1, requiredMultiSelectItem2)
      )
    val optionalMultiSelectItem1 =
      SupportTicketField.MultiSelect.Item(
        title = "Optional MultiSelect Item 1",
        value = "optional-multiselect-item-1"
      )
    val optionalMultiSelectItem2 =
      SupportTicketField.MultiSelect.Item(
        title = "Optional MultiSelect Item 2",
        value = "optional-multiselect-item-2"
      )
    val optionalMultiSelect =
      SupportTicketField.MultiSelect(
        id = 10,
        title = "Optional MultiSelect",
        isRequired = false,
        knownType = null,
        items = listOf(optionalMultiSelectItem1, optionalMultiSelectItem2)
      )

    val requiredPickerItem1 =
      SupportTicketField.Picker.Item(
        title = "Required Picker Item 1",
        value = "required-picker-item-1"
      )
    val requiredPickerItem2 =
      SupportTicketField.Picker.Item(
        title = "Required Picker Item 2",
        value = "required-picker-item-2"
      )

    val requiredPicker =
      SupportTicketField.Picker(
        id = 11,
        title = "Required Picker",
        isRequired = true,
        knownType = null,
        items = listOf(requiredPickerItem1, requiredPickerItem2)
      )
    val optionalPickerItem1 =
      SupportTicketField.Picker.Item(
        title = "Optional Picker Item 1",
        value = "optional-picker-item-1"
      )
    val optionalPickerItem2 =
      SupportTicketField.Picker.Item(
        title = "Optional Picker Item 2",
        value = "optional-picker-item-2"
      )
    val optionalPicker =
      SupportTicketField.Picker(
        id = 12,
        title = "Optional Picker",
        isRequired = false,
        knownType = null,
        items = listOf(optionalPickerItem1, optionalPickerItem2)
      )
  }

  private companion object {
    fun simpleForm(vararg fields: SupportTicketField<*>) =
      SupportTicketForm(
        id = 0,
        fields = fields.toList(),
        conditions = OptimizedSupportTicketFieldConditions(emptyMap())
      )

    fun conditionalForm(
      fields: List<SupportTicketField<*>>,
      conditions: List<SupportTicketFieldCondition>,
    ) = SupportTicketForm(
      id = 0,
      fields = fields,
      conditions = conditions.optimize()
    )

    val Arb.Companion.blankOrMissingText: Arb<String?> get() =
      Arb.string(
        codepoints =
          Arb.of(
            listOf(' ', '\t', '\n', '\r').map { Codepoint(it.code) }
          )
      ).withEdgecases(null, "")

    val Arb.Companion.validText: Arb<String> get() =
      Arb.string(
        minSize = 1
      ).filter { it.isNotBlank() }

    val Arb.Companion.anyText: Arb<String?> get() = blankOrMissingText.merge(validText)

    /**
     * Returns an [Arb] where each value is a [LocalDate], with a random day, and a year in the given range.
     *
     * The default year range is 1970 to the current year, as derived from the system clock and system timezone.
     */
    fun Arb.Companion.date(
      yearRange: IntRange = 1970..Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
    ): Arb<LocalDate> =
      arbitrary {
        LocalDate(
          year = it.random.nextInt(yearRange),
          monthNumber = 1,
          dayOfMonth = 1
        ).plus(it.random.nextInt(0..364), DateTimeUnit.DAY)
      }
  }
}
