#include "bio_storage.h"
#include "grant_protocol.h"
#include "kv.h"
#include "log.h"
#include "onboarding.h"
#include "unlock.h"
#include "wallet.h"
#include "wkek.h"

#include <criterion/criterion.h>

static bool fingerprint_exists;
static bool unlock_secret_provisioned;
static bool wallet_initialized;

void _log(log_level_t level, const char* colour, const char* file, int line, const char* format,
          ...) {
  (void)level;
  (void)colour;
  (void)file;
  (void)line;
  (void)format;
}

bool bio_fingerprint_exists(void) {
  return fingerprint_exists;
}

void bio_wipe_state(void) {}

unlock_err_t unlock_secret_exists(bool* exists) {
  *exists = unlock_secret_provisioned;
  return UNLOCK_OK;
}

void unlock_wipe_state(void) {}

bool wallet_is_initialized(void) {
  return wallet_initialized;
}

void wallet_remove_files(void) {}

bool wkek_exists(void) {
  return false;
}

grant_protocol_result_t grant_protocol_delete_outstanding_request(void) {
  return GRANT_RESULT_OK;
}

grant_protocol_result_t grant_protocol_delete_app_auth_pubkey(void) {
  return GRANT_RESULT_OK;
}

kv_result_t kv_wipe_state(void) {
  return KV_ERR_NONE;
}

static void init(void) {
  fingerprint_exists = false;
  unlock_secret_provisioned = false;
  wallet_initialized = false;
}

TestSuite(onboarding_prod_test, .init = init);

Test(onboarding_prod_test, ignores_unlock_secret_only_state) {
  unlock_secret_provisioned = true;

  cr_assert_eq(onboarding_complete(), SECURE_FALSE);
}

Test(onboarding_prod_test, accepts_fingerprint_state) {
  fingerprint_exists = true;

  cr_assert_eq(onboarding_complete(), SECURE_TRUE);
}

Test(onboarding_prod_test, accepts_initialized_wallet_state) {
  wallet_initialized = true;

  cr_assert_eq(onboarding_complete(), SECURE_TRUE);
}

Test(onboarding_prod_test, accepts_initialized_wallet_with_unlock_secret_state) {
  unlock_secret_provisioned = true;
  wallet_initialized = true;

  cr_assert_eq(onboarding_complete(), SECURE_TRUE);
}
