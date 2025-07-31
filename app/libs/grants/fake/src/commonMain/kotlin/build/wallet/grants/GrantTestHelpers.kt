package build.wallet.grants

/**
 * Test helpers for creating mock Grant and GrantRequest data.
 */
object GrantTestHelpers {
  /**
   * Creates a mock serialized GrantRequest for testing.
   *
   * The structure follows the packed format:
   * - version: Byte (1 byte) - defaults to 1
   * - deviceId: ByteArray (8 bytes) - filled with 0x01
   * - challenge: ByteArray (16 bytes) - filled with 0x02
   * - action: GrantAction (1 byte) - the specified action
   * - signature: ByteArray (64 bytes) - filled with 0x03
   *
   * Total size: 90 bytes
   */
  fun createMockSerializedGrantRequest(
    action: GrantAction,
    version: Byte = 1,
    deviceIdFill: Byte = 0x01,
    challengeFill: Byte = 0x02,
    signatureFill: Byte = 0x03,
  ): ByteArray {
    return ByteArray(SERIALIZED_GRANT_REQUEST_LENGTH).apply {
      set(0, version) // version: 1 byte

      // deviceId: 8 bytes (index 1-8)
      for (i in 1..GRANT_DEVICE_ID_LEN) {
        set(i, deviceIdFill)
      }

      // challenge: 16 bytes (index 9-24)
      val challengeStartIndex = GRANT_VERSION_LEN + GRANT_DEVICE_ID_LEN
      for (i in challengeStartIndex until challengeStartIndex + GRANT_CHALLENGE_LEN) {
        set(i, challengeFill)
      }

      // action: 1 byte (index 25)
      set(ACTION_BYTE_INDEX, action.value.toByte())

      // signature: 64 bytes (index 26-89)
      val signatureStartIndex = GRANT_VERSION_LEN + GRANT_DEVICE_ID_LEN + GRANT_CHALLENGE_LEN + GRANT_ACTION_LEN
      for (i in signatureStartIndex until signatureStartIndex + GRANT_SIGNATURE_LEN) {
        set(i, signatureFill)
      }
    }
  }
}
