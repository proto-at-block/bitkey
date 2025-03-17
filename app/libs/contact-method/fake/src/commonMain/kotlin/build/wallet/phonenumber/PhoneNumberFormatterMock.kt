package build.wallet.phonenumber

class PhoneNumberFormatterMock : PhoneNumberFormatter {
  var formatPartialPhoneNumberResult = ""

  override fun formatPartialPhoneNumber(number: String): String {
    return formatPartialPhoneNumberResult
  }

  fun reset() {
    formatPartialPhoneNumberResult = ""
  }
}
