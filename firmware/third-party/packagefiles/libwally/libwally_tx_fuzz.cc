/*
 * libwally_tx_fuzz.cc - direct libwally transaction/PSBT parser fuzzer.
 *
 * The first fuzz byte selects the parser mode; the remaining bytes are passed
 * unchanged into libwally. Normal parse failures are expected and ignored.
 */

#include <cstddef>
#include <cstdint>
#include <vector>

#include <wally_core.h>
#include <wally_psbt.h>
#include <wally_transaction.h>

namespace {

enum class ParseMode : uint8_t {
  kBitcoinWitness = 0,
  kBitcoinPreBip144 = 1,
  kElements = 2,
  kPsbtStrict = 3,
  kPsbtLoose = 4,
};

bool ensure_wally_initialized() {
  static const int init_result = wally_init(0);
  return init_result == WALLY_OK;
}

void fuzz_tx_parser(const uint8_t* data, size_t size, uint32_t flags) {
  struct wally_tx* tx = nullptr;
  int ret = wally_tx_from_bytes(data, size, flags, &tx);
  if (ret == WALLY_OK && tx != nullptr) {
    wally_tx_free(tx);
  }
}

void fuzz_psbt_parser(const uint8_t* data, size_t size, uint32_t flags) {
  struct wally_psbt* psbt = nullptr;
  int ret = wally_psbt_from_bytes(data, size, flags, &psbt);
  if (ret == WALLY_OK && psbt != nullptr) {
    wally_psbt_free(psbt);
  }
}

}  // namespace

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size == 0 || !ensure_wally_initialized()) {
    return 0;
  }

  const ParseMode mode = static_cast<ParseMode>(data[0] % 5);
  std::vector<uint8_t> payload(data + 1, data + size);
  const uint8_t* payload_data = payload.data();
  const size_t payload_size = payload.size();

  switch (mode) {
    case ParseMode::kBitcoinWitness:
      fuzz_tx_parser(payload_data, payload_size, WALLY_TX_FLAG_USE_WITNESS);
      break;
    case ParseMode::kBitcoinPreBip144:
      fuzz_tx_parser(payload_data, payload_size, WALLY_TX_FLAG_PRE_BIP144);
      break;
    case ParseMode::kElements:
      fuzz_tx_parser(payload_data, payload_size, WALLY_TX_FLAG_USE_ELEMENTS);
      break;
    case ParseMode::kPsbtStrict:
      fuzz_psbt_parser(payload_data, payload_size, WALLY_PSBT_PARSE_FLAG_STRICT);
      break;
    case ParseMode::kPsbtLoose:
      fuzz_psbt_parser(payload_data, payload_size, WALLY_PSBT_PARSE_FLAG_LOOSE);
      break;
  }

  return 0;
}
