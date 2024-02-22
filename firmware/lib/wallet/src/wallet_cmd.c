#include "assert.h"
#include "hex.h"
#include "log.h"
#include "mempool.h"
#include "printf.h"
#include "seed_impl.h"
#include "shell_argparse.h"
#include "shell_cmd.h"
#include "wallet.h"
#include "wallet_impl.h"
#include "wkek.h"

#include <stdlib.h>
#include <string.h>

static struct {
  arg_lit_t* create;
  arg_lit_t* sign;
  arg_lit_t* list;
  arg_str_t* write_seed;
  arg_end_t* end;
} wallet_cmd_args;

extern mempool_t* wallet_pool;

static void cmd_wallet_run(int argc, char** argv);
static void dump_key_bundle(const wallet_key_bundle_type_t type);

static void cmd_wallet_register(void) {
  wallet_cmd_args.create = ARG_LIT_OPT('c', "create", "create a new wallet");
  wallet_cmd_args.sign = ARG_LIT_OPT('s', "sign", "sign a transaction");
  wallet_cmd_args.list = ARG_LIT_OPT('l', "list", "list all stored keys");
  wallet_cmd_args.write_seed = ARG_STR_OPT('w', "write-seed", "write a seed; use with care");
  wallet_cmd_args.end = ARG_END();

  static shell_command_t wallet_cmd = {
    .command = "wallet",
    .help = "wallet operations",
    .handler = cmd_wallet_run,
    .argtable = &wallet_cmd_args,
  };
  shell_command_register(&wallet_cmd);
}
SHELL_CMD_REGISTER("wallet", cmd_wallet_register);

static void dump_key_bundle(const wallet_key_bundle_type_t type) {
  switch (type) {
    case WALLET_KEY_BUNDLE_ACTIVE:
      printf("Active Keys: ");
      break;
    case WALLET_KEY_BUNDLE_RECOVERY:
      printf("Recovery Keys: ");
      break;
    case WALLET_KEY_BUNDLE_INACTIVE:
      printf("Inactive Keys: ");
      break;
    default:
      break;
  }

  if (!wallet_keybundle_exists(type)) {
    printf("None\r\n");
  } else {
    printf("\r\n");
    extended_key_t* pubkey = (extended_key_t*)mempool_alloc(wallet_pool, sizeof(extended_key_t));

    wallet_res_t result = WALLET_RES_ERR;
    const char* domain_names[] = {
      "  Auth  : ",
      "  Config: ",
      "  Spend : ",
    };

    for (uint32_t i = 0; i < WALLET_KEY_DOMAIN_MAX; i++) {
      printf(domain_names[i]);
      result = wallet_get_pubkey(type, (wallet_key_domain_t)i, pubkey);
      if (result == WALLET_RES_OK) {
        dumphex((uint8_t*)pubkey, (sizeof(uint8_t) + BIP32_KEY_SIZE));
      } else {
        printf("error\r\n");
      }
    }

    printf("  Key ID: ");
    uint8_t key_id[SHA256_DIGEST_SIZE] = {0};
    wallet_keybundle_id(type, key_id);
    dumphex(key_id, SHA256_DIGEST_SIZE);

    mempool_free(wallet_pool, pubkey);
  }
}

static void cmd_wallet_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&wallet_cmd_args);

  if (nerrors) {
    return;
  }

  if (wallet_cmd_args.create->header.found) {
    ASSERT(wkek_lazy_init() == true);
    ASSERT(wallet_create_keybundle(WALLET_KEY_BUNDLE_ACTIVE) == WALLET_RES_OK);
  }

  if (wallet_cmd_args.sign->header.found) {
    uint8_t digest[32] = {0};
    uint8_t sig[64] = {0};
    wallet_res_t result = wallet_sign_txn(WALLET_KEY_DOMAIN_SPEND, digest, sig, 1, 0, NULL);
    LOGI("wallet sign: %s", (result == WALLET_RES_OK) ? "ok" : "fail");
    (void)result;
    dumphex(sig, 64);
  }

  if (wallet_cmd_args.list->header.found) {
    dump_key_bundle(WALLET_KEY_BUNDLE_ACTIVE);
    dump_key_bundle(WALLET_KEY_BUNDLE_RECOVERY);
    dump_key_bundle(WALLET_KEY_BUNDLE_INACTIVE);
  }

  if (wallet_cmd_args.write_seed->header.found) {
    uint8_t seed[32] = {0};
    seed_remove_files();
    wkek_lazy_init();
    parsehex(wallet_cmd_args.write_seed->value, wallet_cmd_args.write_seed->len, seed);
    int len = wallet_cmd_args.write_seed->len / 2;
    ASSERT(len == 32);  // seed.c expects 32 byte seeds; although other lengths are technically
                        // valid, we don't use them
    dumphex(seed, len);
    wkek_encrypt_and_store(SEED_PATH, seed, len);
  }
}
