#!/usr/bin/env python3
"""
generate_seeds.py — Generate binary seed corpus files for firmware fuzz targets.

Run from the firmware/fuzz directory:
    python3 fuzzer_inputs/generate_seeds.py

Produces minimal binary seed files that guide libfuzzer toward interesting code
paths on first startup.  Seeds are placed in fuzzer_inputs/<target-name>/.
"""

import os
import struct

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def write_seed(target: str, filename: str, data: bytes) -> None:
    outdir = os.path.join(SCRIPT_DIR, target)
    os.makedirs(outdir, exist_ok=True)
    with open(os.path.join(outdir, filename), "wb") as f:
        f.write(data)
    print(f"  {target}/{filename} ({len(data)} bytes)")


# ---------------------------------------------------------------------------
# wca-session-fuzz seeds (BCW-01, BCW-04, BCW-06, BCW-09, BCW-10)
# FuzzedDataProvider stream: [action:4B][cmd_len:4B][cmd_bytes...]
# ---------------------------------------------------------------------------
WCA_CLA = 0x87
WCA_INS_VERSION   = 0x74
WCA_INS_PROTO     = 0x75
WCA_INS_PROTO_CONT = 0x77
WCA_INS_GET_RESPONSE = 0x78


def wca_seed(action: int, cmd: bytes) -> bytes:
    """Encode one WCA fuzzer iteration: action + cmd_len (as FDP uint32) + cmd."""
    # ConsumeIntegralInRange<int>(0, kNumActions-1) packs action as 4 bytes
    return struct.pack("<I", action) + struct.pack("<I", len(cmd)) + cmd


# VERSION APDU — CLA INS P1 P2 (4 bytes, minimum valid APDU)
version_apdu = bytes([WCA_CLA, WCA_INS_VERSION, 0x00, 0x00])
write_seed("wca-session-fuzz", "version_apdu.bin",
           wca_seed(0, version_apdu))  # kVersion = 0

# PROTO APDU with 5 bytes — triggers normal proto path (BCW-06)
proto_apdu = bytes([WCA_CLA, WCA_INS_PROTO, 0x00, 0x00, 0x01, 0x00])
write_seed("wca-session-fuzz", "proto_apdu.bin",
           wca_seed(1, proto_apdu))   # kProto = 1

# PROTO_CONT with Lc=0 — BCW-09 stale command replay seed
proto_cont_zero = bytes([WCA_CLA, WCA_INS_PROTO_CONT, 0x00, 0x00, 0x00])
write_seed("wca-session-fuzz", "proto_cont_zero_lc.bin",
           wca_seed(3, proto_cont_zero))  # kProtoContZeroLen = 3

# GET_RESPONSE — BCW-10 undrained response bytes
get_response = bytes([WCA_CLA, WCA_INS_GET_RESPONSE, 0x00, 0x00])
write_seed("wca-session-fuzz", "get_response.bin",
           wca_seed(4, get_response))   # kGetResponse = 4

# SESSION_RESET — BCW-09/10 reinit without clearing state
write_seed("wca-session-fuzz", "session_reset.bin",
           struct.pack("<I", 5))  # kSessionReset = 5, no cmd bytes

# ---------------------------------------------------------------------------
# wca-proto-handlers-fuzz seeds (BCW-07, BCW-19, BCW-31)
# FuzzedDataProvider stream: [handler_idx:4B][proto_len:4B][proto_bytes...]
# Empty proto bytes → proto_get_cmd returns NULL → NULL deref detected
# ---------------------------------------------------------------------------
# Select handler 0 (delete_fingerprint) with 1 byte of proto data
write_seed("wca-proto-handlers-fuzz", "delete_fp_empty.bin",
           struct.pack("<I", 0) + struct.pack("<I", 1) + b"\x00")

# Select handler 5 (seal_csek / BCW-31) with short proto data
write_seed("wca-proto-handlers-fuzz", "seal_csek_short.bin",
           struct.pack("<I", 5) + struct.pack("<I", 1) + b"\x00")

# ---------------------------------------------------------------------------
# touch-decode-fuzz seeds (BCW-40)
# FuzzedDataProvider stream: [num_points:1B][touch_data_bytes...]
# ---------------------------------------------------------------------------
FT3169_MAX_TOUCH_POINTS = 2

# num_points = 0 → 2-byte allocation → OOB at touch[0] in switch (BCW-40)
no_touch = bytes([0])  # num_points = 0 (from ConsumeIntegralInRange)
write_seed("touch-decode-fuzz", "no_touch_points.bin",
           no_touch + b"\x00\x00")  # gesture=0, num_points=0

# num_points = 1 → valid single-touch decode
single_touch = bytes([1])  # num_points = 1
touch_point = bytes([
    0x00,  # touch_xh: event_flag=PRESS_DOWN (bits [7:6]=00), x_msb=0
    0x50,  # touch_xl: x=80
    0x01,  # touch_yh: touch_id=0, y_msb=0
    0xA0,  # touch_yl: y=160
    0x01,  # touch_weight
    0x00,  # touch_area + reserved
])
write_seed("touch-decode-fuzz", "single_touch_press.bin",
           single_touch + b"\x00" + b"\x01" + touch_point)

# ---------------------------------------------------------------------------
# indexfs-addr-fuzz seeds (BCW-29)
# FuzzedDataProvider stream: [addr:4B][range_start:4B][range_size:4B] ...
# ---------------------------------------------------------------------------
# addr in range: addr=0x1000, start=0x0, size=0x2000
write_seed("indexfs-addr-fuzz", "addr_in_range.bin",
           struct.pack("<III", 0x1000, 0x0, 0x2000))

# addr == range_start (boundary: in range if size > 0)
write_seed("indexfs-addr-fuzz", "addr_equals_start.bin",
           struct.pack("<III", 0x1000, 0x1000, 0x1))

# range_size == 0 (empty range: must return false)
write_seed("indexfs-addr-fuzz", "empty_range.bin",
           struct.pack("<III", 0x1000, 0x1000, 0x0))

# addr before range_start (must return false)
write_seed("indexfs-addr-fuzz", "addr_before_range.bin",
           struct.pack("<III", 0x0FFF, 0x1000, 0x100))

# UINT32_MAX boundary
write_seed("indexfs-addr-fuzz", "uint32_max.bin",
           struct.pack("<III", 0xFFFFFFFF, 0xFFFFFF00, 0x100))

# ---------------------------------------------------------------------------
# fwup-delta-fuzz seeds (BCW-25, BCW-26)
# FuzzedDataProvider stream: [cmd_type:4B] then cmd fields
# cmd_types: kStart=0, kTransfer=1, kDeltaTransfer=2, kFinish=3
# ---------------------------------------------------------------------------
# Transfer with max sequence_id (BCW-25 overflow trigger)
write_seed("fwup-delta-fuzz", "transfer_max_seq.bin",
           struct.pack("<I", 1) +         # kTransfer
           struct.pack("<I", 0xFFFFFFFF) + # sequence_id (max)
           struct.pack("<I", 0x00) +       # offset
           b"\x01" +                       # ConsumeBool: use actual size
           struct.pack("<H", 1) +          # data length (1 byte)
           b"\x00")                        # data

# Delta transfer (BCW-26: detools patch bytes)
write_seed("fwup-delta-fuzz", "delta_transfer.bin",
           struct.pack("<I", 2) +          # kDeltaTransfer
           struct.pack("<I", 0) +          # sequence_id
           struct.pack("<H", 4) +          # data length
           b"\xd0\x0c\xd0\x01")           # detools patch header bytes

# ---------------------------------------------------------------------------
# nanocobs-fuzz seeds (COBS framing encode/decode round-trip)
# FDP reads integrals from the END and bytes from the FRONT.
# Layout: [payload_bytes][dec_len:8B LE] — trailing 8 bytes are dec_len hint.
# ---------------------------------------------------------------------------
# Single non-zero byte payload → basic encode/decode path
write_seed("nanocobs-fuzz", "single_byte_payload.bin",
           b"\x41" + struct.pack("<Q", 1))

# Payload containing a zero byte → exercises the zero-substitution path
write_seed("nanocobs-fuzz", "payload_with_zero.bin",
           b"\x01\x00\x02" + struct.pack("<Q", 3))

# All-zero payload — every byte is a zero substitute
write_seed("nanocobs-fuzz", "all_zero_payload.bin",
           b"\x00" * 8 + struct.pack("<Q", 8))

# ---------------------------------------------------------------------------
# msgpack-fuzz seeds (raw CMP msgpack bytes — no FDP wrapper)
# The entire input buffer is passed directly to msgpack_mem_access_ro_init().
# ---------------------------------------------------------------------------
write_seed("msgpack-fuzz", "nil.bin",
           bytes([0xC0]))

write_seed("msgpack-fuzz", "bool_true.bin",
           bytes([0xC3]))

write_seed("msgpack-fuzz", "fixint_10.bin",
           bytes([0x0A]))

# str8 "abc"
write_seed("msgpack-fuzz", "str8_abc.bin",
           bytes([0xD9, 0x03, 0x61, 0x62, 0x63]))

# fixmap (empty)
write_seed("msgpack-fuzz", "fixmap_empty.bin",
           bytes([0x80]))

# uint32 0x01020304
write_seed("msgpack-fuzz", "uint32.bin",
           bytes([0xCE, 0x01, 0x02, 0x03, 0x04]))

# ---------------------------------------------------------------------------
# tlv-fuzz seeds (FDP-based; buf contents at front, buf_size at tail)
# FDP reads buf_size (size_t, 8 bytes) from the END, then buf_size bytes
# of buffer content from the FRONT.
# ---------------------------------------------------------------------------
# Empty 32-byte buffer (all zeros → TLV sentinel → empty but valid)
write_seed("tlv-fuzz", "empty_32b_buf.bin",
           bytes(32) + struct.pack("<Q", 32))

# Buffer containing one valid TLV entry (tag=1, len=2, val=0xABCD) + sentinel
_tlv_entry = (
    struct.pack("<I", 1) +   # tag = 1
    struct.pack("<H", 2) +   # length = 2
    bytes([0xAB, 0xCD]) +    # value
    struct.pack("<I", 0) +   # end sentinel tag = 0
    struct.pack("<H", 0)     # end sentinel length = 0
)
write_seed("tlv-fuzz", "one_entry.bin",
           _tlv_entry + bytes(32 - len(_tlv_entry)) + struct.pack("<Q", 32))

# ---------------------------------------------------------------------------
# iso7816-fuzz seeds (FDP-based: buf_size at tail, buf bytes at front)
# Each call: buf (1–4 bytes at front), buf_size (size_t, 8 bytes at tail).
# ---------------------------------------------------------------------------
# buf[0] = 0x01 → standard Lc encoding (no extended read)
write_seed("iso7816-fuzz", "lc_standard_1.bin",
           bytes([0x01]) + struct.pack("<Q", 1))

# buf = [0x00, 0x00, 0x05] → extended Lc encoding (valid, length = 5)
write_seed("iso7816-fuzz", "lc_extended_valid.bin",
           bytes([0x00, 0x00, 0x05]) + struct.pack("<Q", 3))

# buf = [0x00] (1 byte only) → extended coding but buf too short — the bug
write_seed("iso7816-fuzz", "lc_extended_1byte.bin",
           bytes([0x00]) + struct.pack("<Q", 1))

# ---------------------------------------------------------------------------
# sap-fuzz seeds (FDP-based)
# FDP layout (all from END, ConsumeBool/ConsumeIntegral):
#   valid (1B) and version (4B) from the END, then action/value/bindings from FRONT.
# ---------------------------------------------------------------------------
# "SEND_BTC" action — exercises sap_parse_action string comparison
write_seed("sap-fuzz", "send_btc_action.bin",
           b"SEND_BTC\x00" + bytes(63 - 9) +   # action[63]
           bytes(127) +                          # value[127]
           bytes(255) +                          # bindings[255]
           struct.pack("<I", 1) + b"\x01")       # version=1, valid=true (from end)

# Empty action — exercises the unknown-action fallback
write_seed("sap-fuzz", "empty_action.bin",
           bytes(63) + bytes(127) + bytes(255) +
           struct.pack("<I", 1) + b"\x01")

# ---------------------------------------------------------------------------
# psbt-fuzz seeds
# FDP layout: psbt_len (size_t, 8B) at tail, then psbt_len bytes at front.
# PSBT magic header: "psbt\xff" (0x70 0x73 0x62 0x74 0xFF).
# ---------------------------------------------------------------------------
PSBT_MAGIC = bytes([0x70, 0x73, 0x62, 0x74, 0xFF])

write_seed("psbt-fuzz", "psbt_magic_only.bin",
           PSBT_MAGIC + struct.pack("<Q", 5) + bytes(64))  # +64B for sig path

# PSBT magic + global separator (0x00) = minimal structurally-plausible header
write_seed("psbt-fuzz", "psbt_minimal.bin",
           PSBT_MAGIC + b"\x00" + struct.pack("<Q", 6) + bytes(64))

# ---------------------------------------------------------------------------
# libwally-tx-fuzz seeds
# Layout: first byte selects parser mode; remaining bytes are raw parser input.
# Modes: 0=BTC witness tx, 1=BTC pre-BIP144 tx, 2=Elements tx,
#        3=PSBT/PSET strict, 4=PSBT/PSET loose.
# ---------------------------------------------------------------------------
MINIMAL_BTC_TX = (
    struct.pack("<I", 1) +      # version
    b"\x01" +                   # one input
    bytes(32) +                 # prev txid
    struct.pack("<I", 0xffffffff) +
    b"\x00" +                   # empty scriptSig
    struct.pack("<I", 0xffffffff) +
    b"\x01" +                   # one output
    struct.pack("<Q", 0) +
    b"\x00" +                   # empty scriptPubKey
    struct.pack("<I", 0)        # lock_time
)

write_seed("libwally-tx-fuzz", "btc_minimal_pre_bip144.bin",
           bytes([1]) + MINIMAL_BTC_TX)

write_seed("libwally-tx-fuzz", "psbt_minimal_strict.bin",
           bytes([3]) + PSBT_MAGIC + b"\x00")

# Elements tx: version || elements witness flag || input_count || output_count.
# Ends immediately before the first output commitment prefix.
write_seed("libwally-tx-fuzz", "elements_output_commitment_prefix_missing.bin",
           bytes([2]) + struct.pack("<I", 1) + b"\x00\x00\x01")

# Same path, but with an explicit commitment prefix and no committed value body.
write_seed("libwally-tx-fuzz", "elements_output_commitment_body_missing.bin",
           bytes([2]) + struct.pack("<I", 1) + b"\x00\x00\x01\x01")

# ---------------------------------------------------------------------------
# grant-protocol-fuzz seeds
# FDP layout: sizeof(grant_t) bytes at FRONT, then flags from END.
# Flags (from end): has_request (1B), has_pubkey (1B), if has_request: match_request (1B).
# sizeof(grant_t) ≈ 219 bytes (version:1 + serialized_request:90 + app_sig:64 + wsm_sig:64).
# ---------------------------------------------------------------------------
_GRANT_T_SIZE = 219

write_seed("grant-protocol-fuzz", "no_request_no_pubkey.bin",
           bytes(_GRANT_T_SIZE) +
           b"\x00" +          # has_pubkey = false (END)
           b"\x00")           # has_request = false (END, read first via ConsumeBool)

write_seed("grant-protocol-fuzz", "has_request_matching.bin",
           bytes(_GRANT_T_SIZE) +
           b"\x01" +          # has_pubkey = true
           b"\x01" +          # match_request = true
           b"\x01")           # has_request = true

write_seed("grant-protocol-fuzz", "has_request_no_pubkey.bin",
           bytes(_GRANT_T_SIZE) +
           b"\x00" +          # has_pubkey = false
           b"\x00" +          # match_request = false
           b"\x01")           # has_request = true

# ---------------------------------------------------------------------------
# picocert-fuzz seeds
# FDP layout: sizeof(picocert_t) bytes per cert at FRONT, chain_len at END.
# sizeof(picocert_t): version(1)+issuer(32)+subject(32)+valid_from(8)+valid_to(8)+
#   curve(4)+hash(4)+reserved(4)+public_key(65)+signature(64) = 222 bytes + padding.
# ---------------------------------------------------------------------------
_PICOCERT_T_SIZE = 224  # rounded up for alignment

write_seed("picocert-fuzz", "single_cert_zero.bin",
           bytes(_PICOCERT_T_SIZE) + struct.pack("<Q", 1))

write_seed("picocert-fuzz", "two_cert_chain.bin",
           bytes(_PICOCERT_T_SIZE * 2) + struct.pack("<Q", 2))

# ---------------------------------------------------------------------------
# unlock-fuzz seeds
# sizeof(unlock_secret_t) = SHA256_DIGEST_SIZE = 32 bytes.
# FDP layout: 32-byte secret at FRONT (ConsumeBytes); remaining attempts also FRONT.
# ---------------------------------------------------------------------------
_UNLOCK_SECRET_SIZE = 32  # SHA256_DIGEST_SIZE

# Provision secret 0x01*32 and attempt a correct match
write_seed("unlock-fuzz", "correct_secret.bin",
           bytes([0x01] * _UNLOCK_SECRET_SIZE) +    # provision
           bytes([0x01] * _UNLOCK_SECRET_SIZE))      # correct attempt

# Provision secret 0x01*32 and attempt wrong secret 0x02*32
write_seed("unlock-fuzz", "wrong_secret.bin",
           bytes([0x01] * _UNLOCK_SECRET_SIZE) +    # provision
           bytes([0x02] * _UNLOCK_SECRET_SIZE))      # wrong attempt

# Multiple wrong attempts in a row — exercises retry-counter increment
write_seed("unlock-fuzz", "multi_wrong_attempts.bin",
           bytes([0x01] * _UNLOCK_SECRET_SIZE) +    # provision
           bytes([0x02] * _UNLOCK_SECRET_SIZE) +    # wrong 1
           bytes([0x03] * _UNLOCK_SECRET_SIZE) +    # wrong 2
           bytes([0x04] * _UNLOCK_SECRET_SIZE))     # wrong 3

print("\nSeed generation complete.")
