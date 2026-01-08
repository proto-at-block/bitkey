# UC (UXC COBS)

## Overview

This library builds upon a COBS (Consistent Overhead Byte Stuffing) to implement
a comms layer for exchanging proto messages between a UXC (User Experience Chip)
and Host.

## Design

### Encoding

* Messages are encoded using COBS and sent over a serial interface between a
  Host and device.
* For a reduced memory and code footprint, a `nanocobs` implementation is used;
  this implementation can be found under the `third-party` subfolder.

### Messaging

* Messages exchanged are protobufs.
* Since protobufs do not provide the message integrity checks, re-transmissions,
  etc., these are implemented by the UC library using a message header.

## Data Flow

### Sending a Message

1. CRC for data is generated.
2. Send sequence number is incremented.
3. Header for the message is generated:
   a. CRC is written to the header.
   b. ACK sequence number is the last received message.
   c. New send sequence number.
4. Header and data are COBS encoded then sent over the serial interface.

### Receiving a Message

1. Serial interface is read one byte at a time until the COBS Start-of-Frame
   (SOF) (0x00) is found.
2. Header payload bytes is read from the USART.
3. If a COBS SOF is found in the header, then we restart from step 1 from that
   offset.
   a. This is to address instances where a message is not sent completely and a
      new message is being sent instead (e.g. on crash).
4. CRC of payload is validated.
   a. On failure, a NACK is sent.
5. If a message is encrypted, decryption takes place.
   a. On failure, a NACK is sent.
6. ACK sequence number is updated.
7. ACK timer is started if not running; restarted if running.
8. Unencrypted / plaintext message is forwarded to the registered handler.
9. If the ACK timer fires, a pure ACK message is sent.

### Acknowledging a Message

* Numbers 1 - 255 are used for sequence numbers.
* Each message contains a send sequence number of the message being sent and
  an ACK sequence number acknowledging a previously received message.
* Messages with a sequence number equal to the last received ACK sequence number
  will be dropped by the recipient (assumed to already have received it).
* Upon receipt of a message, an ACK timer is started. If no message is sent by
  the time the ACK timer expires, an ACK message is sent.
  * ACK message is an empty payload message with the flag indicating it is an ACK.
  * On sending of an ACK, the ACK leaves the sequence number as-is.
* Upon sending a message, the sender waits for the ACK timeout to expire.
  * Send sequence number is set to the next sequence number.
  * On ACK timeout, a message is re-transmitted.
  * Messages are re-transmitted up to a defined number.
* ACKs are not sent if:
  * The receiver fails to receive the message.
  * CRC check fails.
* If an already ACK'd message is received, the message is dropped but an ACK is sent back.
* ACKs are not re-transmitted.

## Integrity and Encryption

### Message Integrity

Each message header will contain a 16-bit CRC for the message header and body.
The CRC will be used to validate the integrity of the message, dropping the
message if the CRC check fails.

### Encryption

* If data must be encrypted before sending, it is encrypted prior to sending
  using the chip's stored certificate.
* After encryption, the signature is attached.
* The payload length in the message indicates the total size of the encrypted
  data + signature.
* On sending, the encrypted message + header are COBS encoded and sent to the
  recipient.
* Upon receipt, the recipient will decrypt the data after COBS decoding has
  taken place.
* The recipient knows (based on the header) that the message is encrypted and
  would need to decrypt + verify the signature.
