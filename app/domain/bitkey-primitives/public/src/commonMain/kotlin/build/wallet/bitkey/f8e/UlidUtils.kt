package build.wallet.bitkey.f8e

import bitkey.serialization.base32.Base32Encoding
import com.github.michaelbull.result.getOr
import okio.Buffer

/**
 * Extracts the timestamp from a server ID URN containing a ULID.
 * Server IDs are in the format: urn:wallet-account:01HW6PK7Q9YMW0B9VQCVN1FMHC
 * ULIDs encode a 48-bit timestamp in the first 10 characters (Crockford Base32).
 * Returns the timestamp in milliseconds since Unix epoch, or 0 if parsing fails.
 */
fun extractUlidTimestampFromUrn(serverId: String): Long {
  // Extract ULID from URN format (urn:wallet-account:ULID)
  val ulid = serverId.substringAfterLast(':')
  if (ulid.length < 10) return 0L

  val timestampPart = ulid.take(10)

  // Decode the timestamp part using Crockford Base32
  val decodedBytes = Base32Encoding.decode(timestampPart).getOr(null)
    ?: return 0L

  // ULID timestamp is 48 bits (6 bytes), pad to 8 for Long
  val buffer = Buffer()
  buffer.writeByte(0)  // Pad 2 bytes for 64-bit long
  buffer.writeByte(0)
  buffer.write(decodedBytes.toByteArray())

  return buffer.readLong()
}
