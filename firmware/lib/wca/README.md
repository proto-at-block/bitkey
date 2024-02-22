# WCA — Wallet Custom APDUs

WCA (Wallet Custom APDUs) is a library which uses ISO7816 [APDUs](https://en.wikipedia.org/wiki/Smart_card_application_protocol_data_unit) to encapsulate and
transport protobufs.

WCA uses a custom command class (`0x87`) and instructions, defined in `wca.h`

## Commands

All commands require the first four bytes of a APDU header.
All responses are followed by two bytes of status words (see `iso7816.h`).

### Version

| CLA  | INS  | P1   | P2   | Lc   | Data |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 0x87 | 0x74 | 0x00 | 0x00 | -    | -    |

Get the current version of the WCA library on the hardware.


### Proto

| CLA  | INS  | P1   | P2   | Lc   | Data |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 0x87 | 0x75 | Proto size | Proto size | Data length | See below |

Send a protobuf. If a protobuf fits entirely in the APDU size, then no other commands
are required. If the protobuf is too big and needs to be split (larger than `MAX_PROTO_SIZE` see below), the `PROTO_CONT` instruction
needs to be sent out afterwards.

The protobuf sent must be a single type using `oneof` inside to select an actual payload.

`P1` + `P2` combined form a `uint16_t` containing the full proto size. The size is sent big-endian
(so `P1` is the high byte).

`Data` is the encoded protobuf.

### Proto Continuation

| CLA  | INS  | P1   | P2   | Lc   | Data |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 0x87 | 0x77 | Proto size | Proto size | Data length | Encoded proto |

Continue sending a protobuf, split into parts. This command can be repeated until the entire
protobuf is sent. This command is valid after `PROTO`.

### Get Response

| CLA  | INS  | P1   | P2   | Lc   | Data |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 0x87 | 0x78 | 0x00 | 0x00 | -    | -    |

Get the rest of the pending response.

If the status words are `0x90 0x00`, then the APDU contains the full response.
If the status words are `0x61 0xnn`, then `GET_RESPONSE` must be issued to receive the
rest of the response. `SW2` is `0xff` if there are > 255 bytes remaining, and the remaining
number of bytes otherwise.

## Sizes
The maximum size of the ADPUs is configurable, so the size at which to use `PROTO_CONT` depends on that size.
- `MAX_WCA_BUFFER_SIZE`: The configured maximum size of an ADPU. Currently 512.
- `APDU_OVERHEAD_SIZE`: The size of the APDU header. Either 5 or 7 bytes.
```
APDU_OVERHEAD_SIZE = CLA (1) + INS (1) + P1 (1) + P2 (1) + Lc (1 or 3)
```
Lc will use 3 bytes if the size of the proto is > 256, otherwise, it will use 1 byte.

- `MAX_PROTO_SIZE`: The maximum size of proto data for an ADPU.
```
MAX_PROTO_SIZE = MAX_WCA_BUFFER_SIZE - APDU_OVERHEAD_SIZE
```

## Example Sequence Diagrams
![image](https://user-images.githubusercontent.com/30729153/180082456-05c14467-9342-400f-bec0-30b3642710ec.png)
