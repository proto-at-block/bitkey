package build.wallet.phonenumber

class PhoneNumberValidatorMock : PhoneNumberValidator {
  var dialingCodeForCurrentRegion: Int = 1

  override fun dialingCodeForCurrentRegion(): Int = dialingCodeForCurrentRegion

  var exampleFormattedNumberForCurrentRegion: String? = null

  override fun exampleFormattedNumberForCurrentRegion(): String? =
    exampleFormattedNumberForCurrentRegion

  var validatePhoneNumberResult: PhoneNumber? = null

  override fun validatePhoneNumber(number: String): PhoneNumber? {
    return validatePhoneNumberResult
  }

  fun reset() {
    dialingCodeForCurrentRegion = 1
    exampleFormattedNumberForCurrentRegion = null
    validatePhoneNumberResult = null
  }
}
