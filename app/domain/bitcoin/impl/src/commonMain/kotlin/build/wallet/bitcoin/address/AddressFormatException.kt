package build.wallet.bitcoin.address

internal sealed class AddressFormatException(message: String) : IllegalArgumentException(message) {
  /**
   * This exception is thrown by [Bech32] when you try to decode data and a character isn't valid.
   * You shouldn't allow the user to proceed in this case.
   */
  class InvalidCharacter(character: Char, position: Int) :
    AddressFormatException("Invalid character '$character' at position $position")

  /**
   * This exception is thrown by [Bech32] when you try to decode data and the data isn't of the
   * right size. You shouldn't allow the user to proceed in this case.
   */
  class InvalidDataLength(message: String) : AddressFormatException(message)

  /**
   * This exception is thrown by [Bech32] when you try to decode data and the checksum isn't valid.
   * You shouldn't allow the user to proceed in this case.
   */
  class InvalidChecksum : AddressFormatException("Checksum does not validate")

  /**
   * This exception is thrown by [Bech32] when you try and decode an
   * address or private key with an invalid prefix (version header or human-readable part). You shouldn't allow the
   * user to proceed in this case.
   */
  class InvalidPrefix(message: String) : AddressFormatException(message)

  class InvalidVersion(version: Byte) :
    AddressFormatException("Invalid version '$version")
}
