package build.wallet.nfc

import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.SignatureUtils
import build.wallet.grants.GRANT_CHALLENGE_LEN
import build.wallet.grants.GRANT_DEVICE_ID_LEN
import build.wallet.grants.GRANT_MESSAGE_PREFIX
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/**
 * Builds a fake [GrantRequest] signed with the given key store's auth key.
 * Shared by both W1 and W3 fake command implementations.
 */
internal suspend fun buildFakeGrantRequest(
  keyStore: FakeHardwareKeyStore,
  deviceSerial: String,
  action: GrantAction,
  messageSigner: MessageSigner,
  signatureUtils: SignatureUtils,
): GrantRequest {
  val challengeBytes = SecureRandom().nextBytes(GRANT_CHALLENGE_LEN)

  val deviceIdBytes = deviceSerial.encodeUtf8().toByteArray()
  val deviceId = if (deviceIdBytes.size >= GRANT_DEVICE_ID_LEN) {
    deviceIdBytes.sliceArray(0 until GRANT_DEVICE_ID_LEN)
  } else {
    deviceIdBytes + ByteArray(GRANT_DEVICE_ID_LEN - deviceIdBytes.size)
  }

  val version = 1.toByte()

  val messageToSign = Buffer().apply {
    write(GRANT_MESSAGE_PREFIX.encodeUtf8())
    writeByte(version.toInt())
    write(deviceId)
    write(challengeBytes)
    writeByte(action.value)
  }.readByteString().toByteArray()

  val authKey = keyStore.getAuthKeypair().privateKey.key
  val derSignatureHex = messageSigner.sign(messageToSign.toByteString(), authKey)
  val derSignatureByteString = derSignatureHex.decodeHex().toByteArray().toByteString()
  val compactSignature = signatureUtils.decodeSignatureFromDer(derSignatureByteString)

  val serialized = Buffer().apply {
    writeByte(version.toInt())
    write(deviceId)
    write(challengeBytes)
    writeByte(action.value)
    write(compactSignature)
  }.readByteString()

  return GrantRequest.fromBytes(serialized)
    ?: throw NfcException.CommandError("Failed to create GrantRequest from serialized data")
}
