import build.wallet.phonenumber.PhoneNumberLibBindingsImpl
import build.wallet.phonenumber.PhoneNumberValidatorImpl
import build.wallet.platform.settings.CountryCodeGuesserMock
import com.google.i18n.phonenumbers.CountryCodeToRegionCodeMap
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class PhoneNumberValidatorImplTests : FunSpec({
  val countryCodeGuesser = CountryCodeGuesserMock()
  val countryCodeToRegionMap = CountryCodeToRegionCodeMap.getCountryCodeToRegionCodeMap()
  val phoneUtil = PhoneNumberUtil.getInstance()
  val validator =
    PhoneNumberValidatorImpl(
      countryCodeGuesser = countryCodeGuesser,
      phoneNumberLibBindings = PhoneNumberLibBindingsImpl()
    )

  beforeTest {
    countryCodeGuesser.countryCode = "US"
  }

  test("dialing code for region") {
    countryCodeGuesser.countryCode = "US"
    validator.dialingCodeForCurrentRegion()
      .shouldBe(1)

    countryCodeGuesser.countryCode = "XX"
    validator.dialingCodeForCurrentRegion()
      .shouldBe(1)

    countryCodeToRegionMap
      .filter { !it.value.contains("001") }
      .forEach { (countryCode, regionCodes) ->
        regionCodes.forEach { regionCode ->
          countryCodeGuesser.countryCode = regionCode
          validator.dialingCodeForCurrentRegion()
            .shouldBe(countryCode)
        }
      }
  }

  test("example number for region") {
    validator.exampleFormattedNumberForCurrentRegion()
      .shouldBe("+1 201-555-0123")

    countryCodeToRegionMap
      .filter { !it.value.contains("001") }
      .forEach { (_, regionCodes) ->
        regionCodes.forEach { regionCode ->
          countryCodeGuesser.countryCode = regionCode
          validator.exampleFormattedNumberForCurrentRegion()
            .shouldNotBeNull()
        }
      }
  }

  test("validate phone number with raw number") {
    countryCodeToRegionMap.forEach { (_, regions) ->
      regions.filter { region -> region.all { it.isLetter() } }.forEach { regionCode ->
        val validNationalNumber =
          phoneUtil.format(
            phoneUtil.getExampleNumber(regionCode),
            INTERNATIONAL
          )
        validator.validatePhoneNumber(validNationalNumber).shouldNotBeNull()

        val invalidNationalNumber =
          phoneUtil.format(
            phoneUtil.getInvalidExampleNumber(regionCode),
            INTERNATIONAL
          )
        validator.validatePhoneNumber(invalidNationalNumber).shouldBeNull()
      }
    }
  }

  test("validate phone number with raw number invalid string") {
    validator.validatePhoneNumber("foo-bar").shouldBeNull()
  }

  test("validate phone number with E164 format") {
    countryCodeToRegionMap.forEach { (_, regions) ->
      regions.filter { region -> region.all { it.isLetter() } }.forEach { regionCode ->
        val validE164 = phoneUtil.format(phoneUtil.getExampleNumber(regionCode), E164)
        validator.validatePhoneNumber(validE164).shouldNotBeNull()

        val invalidE164 = phoneUtil.format(phoneUtil.getInvalidExampleNumber(regionCode), E164)
        validator.validatePhoneNumber(invalidE164).shouldBeNull()
      }
    }
  }

  test("validate phone number with E164 format invalid string") {
    validator.validatePhoneNumber("foo-bar").shouldBeNull()
  }
})
