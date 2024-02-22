#include "FuzzedDataProvider.h"
extern "C" {
#include "bip32.h"
#include "secutils.h"
}

#include <stddef.h>
#include <stdint.h>

bool bio_fingerprint_exists(void) {
  return true;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  if (size < BIP32_SERIALIZED_EXT_KEY_SIZE) {
    return 0;
  }

  if (fuzzed_data.remaining_bytes() > 0) {
    extended_key_t key;
    key.prefix =
      (fuzzed_data.ConsumeBool()) ? SEC1_COMPRESSED_PUBKEY_EVEN : SEC1_COMPRESSED_PUBKEY_ODD;
    std::vector<uint8_t> data = fuzzed_data.ConsumeBytes<uint8_t>(BIP32_KEY_SIZE);
    memcpy(key.key, data.data(), data.size());
    data = fuzzed_data.ConsumeBytes<uint8_t>(BIP32_CHAINCODE_SIZE);
    memcpy(key.chaincode, data.data(), data.size());

    extended_key_t parent_pub;
    parent_pub.prefix =
      (fuzzed_data.ConsumeBool()) ? SEC1_COMPRESSED_PUBKEY_EVEN : SEC1_COMPRESSED_PUBKEY_ODD;
    data = fuzzed_data.ConsumeBytes<uint8_t>(BIP32_KEY_SIZE);
    memcpy(parent_pub.key, data.data(), data.size());
    data = fuzzed_data.ConsumeBytes<uint8_t>(BIP32_CHAINCODE_SIZE);
    memcpy(parent_pub.chaincode, data.data(), data.size());

    uint8_t parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE];
    version_bytes_t version = TESTNET_PUB;
    uint32_t child_num = fuzzed_data.ConsumeIntegral<uint32_t>();
    uint32_t depth = fuzzed_data.ConsumeIntegral<uint8_t>();

    uint8_t serialized[BIP32_SERIALIZED_EXT_KEY_SIZE];

    // three cases
    // 0: no parent_pub and parent_fingerprint (wallet.c uses this exclusively)
    // 1: can have parent_pub and no parent_fingerprint
    // 2: or both (but they must match)

    int choice = fuzzed_data.ConsumeIntegralInRange(0, 2);
    switch (choice) {
      case 0:
        bip32_serialize_ext_key(&key, NULL, parent_fingerprint, version, child_num, depth,
                                serialized, sizeof(serialized));
        break;
      case 1:
        bip32_serialize_ext_key(&key, &parent_pub, NULL, version, child_num, depth, serialized,
                                sizeof(serialized));
        break;
      case 2:
        // 5 = sizeof(be_version) + sizeof(depth)
        bip32_compute_fingerprint(&parent_pub, &serialized[5]);
        memcpy(parent_fingerprint, &serialized[5], BIP32_KEY_FINGERPRINT_SIZE);
        memset(serialized, 0, BIP32_SERIALIZED_EXT_KEY_SIZE);
        bip32_serialize_ext_key(&key, &parent_pub, parent_fingerprint, version, child_num, depth,
                                serialized, sizeof(serialized));
        break;
      default:
        break;
    }
  }

  return 0;
}
