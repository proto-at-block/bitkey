#include "criterion_test_utils.h"
#include "fff.h"
#include "sign_action_proof_core.h"

#include <criterion/criterion.h>

#include <string.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, rtos_in_isr);

// ---------------------------------------------------------------------------
// Action Parsing
// ---------------------------------------------------------------------------

Test(sap_parse, parse_action_valid) {
  cr_assert_eq(sap_parse_action("SetSpendWithoutHardware"), SAP_ACTION_SET_SPEND_WITHOUT_HARDWARE);
  cr_assert_eq(sap_parse_action("DisableSpendWithoutHardware"),
               SAP_ACTION_DISABLE_SPEND_WITHOUT_HARDWARE);
  cr_assert_eq(sap_parse_action("SetVerificationThreshold"), SAP_ACTION_SET_VERIFICATION_THRESHOLD);
  cr_assert_eq(sap_parse_action("SetRecoveryEmail"), SAP_ACTION_SET_RECOVERY_EMAIL);
  cr_assert_eq(sap_parse_action("DisableRecoveryEmail"), SAP_ACTION_DISABLE_RECOVERY_EMAIL);
  cr_assert_eq(sap_parse_action("SetRecoveryPhone"), SAP_ACTION_SET_RECOVERY_PHONE);
  cr_assert_eq(sap_parse_action("DisableRecoveryPhone"), SAP_ACTION_DISABLE_RECOVERY_PHONE);
  cr_assert_eq(sap_parse_action("SetRecoveryPushNotifications"),
               SAP_ACTION_SET_RECOVERY_PUSH_NOTIFICATIONS);
  cr_assert_eq(sap_parse_action("DisableRecoveryPushNotifications"),
               SAP_ACTION_DISABLE_RECOVERY_PUSH_NOTIFICATIONS);
  cr_assert_eq(sap_parse_action("AddRecoveryContact"), SAP_ACTION_ADD_RECOVERY_CONTACT);
  cr_assert_eq(sap_parse_action("RemoveRecoveryContact"), SAP_ACTION_REMOVE_RECOVERY_CONTACT);
  cr_assert_eq(sap_parse_action("AddBeneficiary"), SAP_ACTION_ADD_BENEFICIARY);
  cr_assert_eq(sap_parse_action("RemoveBeneficiary"), SAP_ACTION_REMOVE_BENEFICIARY);
  cr_assert_eq(sap_parse_action("AcceptRecoveryContactsInvite"),
               SAP_ACTION_ACCEPT_RECOVERY_CONTACTS_INVITE);
  cr_assert_eq(sap_parse_action("AcceptBeneficiariesInvite"),
               SAP_ACTION_ACCEPT_BENEFICIARIES_INVITE);
  cr_assert_eq(sap_parse_action("CreateLostAppRecovery"), SAP_ACTION_CREATE_LOST_APP_RECOVERY);
  cr_assert_eq(sap_parse_action("CancelLostAppRecovery"), SAP_ACTION_CANCEL_LOST_APP_RECOVERY);
  cr_assert_eq(sap_parse_action("DeleteAccount"), SAP_ACTION_DELETE_ACCOUNT);
  cr_assert_eq(sap_parse_action("RotateAppAuthKeys"), SAP_ACTION_ROTATE_APP_AUTH_KEYS);
  cr_assert_eq(sap_parse_action("UpdateDescriptorBackups"), SAP_ACTION_UPDATE_DESCRIPTOR_BACKUPS);
  cr_assert_eq(sap_parse_action("RotateSpendingKeyset"), SAP_ACTION_ROTATE_SPENDING_KEYSET);
  cr_assert_eq(sap_parse_action("EekRestoration"), SAP_ACTION_EEK_RESTORATION);
  cr_assert_eq(sap_parse_action("FullAccountCloudBackupRestore"),
               SAP_ACTION_FULL_ACCOUNT_CLOUD_BACKUP_RESTORE);
  cr_assert_eq(sap_parse_action("ApproveAppRecovery"), SAP_ACTION_APPROVE_APP_RECOVERY);
  cr_assert_eq(sap_parse_action("StartRecovery"), SAP_ACTION_START_RECOVERY);
  cr_assert_eq(sap_parse_action("CompleteWallet"), SAP_ACTION_COMPLETE_WALLET);
  cr_assert_eq(sap_parse_action("UpgradeWallet"), SAP_ACTION_UPGRADE_WALLET);
  cr_assert_eq(sap_parse_action("CancelLostHardwareRecovery"),
               SAP_ACTION_CANCEL_LOST_HARDWARE_RECOVERY);
  cr_assert_eq(sap_parse_action("CancelConflictingRecovery"),
               SAP_ACTION_CANCEL_CONFLICTING_RECOVERY);
  cr_assert_eq(sap_parse_action("InitiateWalletUpgrade"), SAP_ACTION_INITIATE_WALLET_UPGRADE);
  cr_assert_eq(sap_parse_action("RemoveRecoveryCustomer"), SAP_ACTION_REMOVE_RECOVERY_CUSTOMER);
  cr_assert_eq(sap_parse_action("RemoveBenefactor"), SAP_ACTION_REMOVE_BENEFACTOR);
}

Test(sap_parse, parse_action_unknown) {
  cr_assert_eq(sap_parse_action("BadAction"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action(""), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("Delete"), SAP_ACTION_COUNT);
  // Old-style separate action strings are no longer valid
  cr_assert_eq(sap_parse_action("Add"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("Set"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("Remove"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("Disable"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("Accept"), SAP_ACTION_COUNT);
}

Test(sap_parse, parse_action_case_sensitive) {
  cr_assert_eq(sap_parse_action("setrecoveryemail"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("SETRECOVERYEMAIL"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("setspendwithouthardware"), SAP_ACTION_COUNT);
  cr_assert_eq(sap_parse_action("SETSPENDWITHOUTHARDWARE"), SAP_ACTION_COUNT);
}

Test(sap_parse, parse_action_null) {
  cr_assert_eq(sap_parse_action(NULL), SAP_ACTION_COUNT);
}

// ---------------------------------------------------------------------------
// Enum Counts
// ---------------------------------------------------------------------------

Test(sap_validation, enum_counts) {
  cr_assert_eq(SAP_ACTION_COUNT, 32, "Expected 32 combined actions");
  cr_assert_eq(SAP_ACTION_EEK_RESTORATION, 21, "Existing IPC-carried SAP IDs must remain stable");
  cr_assert_eq(SAP_ACTION_FULL_ACCOUNT_CLOUD_BACKUP_RESTORE, 22,
               "Existing IPC-carried SAP IDs must remain stable");
  cr_assert_eq(SAP_ACTION_APPROVE_APP_RECOVERY, 23, "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_START_RECOVERY, 24, "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_COMPLETE_WALLET, 25, "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_UPGRADE_WALLET, 26, "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_CANCEL_LOST_HARDWARE_RECOVERY, 27,
               "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_CANCEL_CONFLICTING_RECOVERY, 28,
               "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_INITIATE_WALLET_UPGRADE, 29, "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_REMOVE_RECOVERY_CUSTOMER, 30, "New SAP actions must append at the end");
  cr_assert_eq(SAP_ACTION_REMOVE_BENEFACTOR, 31, "New SAP actions must append at the end");
}

Test(sap_validation, strings_array_matches_enum_count) {
  // Ensure sap_action_strings[] has exactly SAP_ACTION_COUNT entries.
  // A mismatch causes out-of-bounds reads during action parsing.
  cr_assert_eq(sizeof(sap_action_strings) / sizeof(sap_action_strings[0]), SAP_ACTION_COUNT,
               "sap_action_strings count must match SAP_ACTION_COUNT");
}

// ---------------------------------------------------------------------------
// Canonical Payload Construction
// ---------------------------------------------------------------------------

Test(sap_payload, add_recovery_contact_with_value) {
  sap_pending_data_t data = {0};
  data.valid = true;
  strncpy(data.action, "AddRecoveryContact", sizeof(data.action) - 1);
  strncpy(data.value, "Alice", sizeof(data.value) - 1);
  strncpy(data.bindings, "eid=01HQXYZ123,n=42", sizeof(data.bindings) - 1);

  uint8_t buf[SAP_PAYLOAD_MAX_SIZE] = {0};
  int len = sap_build_payload(&data, buf, sizeof(buf));
  cr_assert_gt(len, 0, "Payload build failed");

  const uint8_t expected[] =
    "ACTIONPROOF\x1f"
    "1\x1f"
    "AddRecoveryContact\x1f"
    "Alice\x1f"
    "eid=01HQXYZ123,n=42";
  cr_assert_eq((size_t)len, sizeof(expected) - 1, "Payload length mismatch: got %d, expected %zu",
               len, sizeof(expected) - 1);
  cr_util_cmp_buffers(buf, expected, (size_t)len);
}

Test(sap_payload, disable_spend_without_hardware_no_value) {
  sap_pending_data_t data = {0};
  data.valid = true;
  strncpy(data.action, "DisableSpendWithoutHardware", sizeof(data.action) - 1);
  strncpy(data.bindings, "eid=01HRABC456,n=7", sizeof(data.bindings) - 1);

  uint8_t buf[SAP_PAYLOAD_MAX_SIZE] = {0};
  int len = sap_build_payload(&data, buf, sizeof(buf));
  cr_assert_gt(len, 0, "Payload build failed");

  const uint8_t expected[] =
    "ACTIONPROOF\x1f"
    "1\x1f"
    "DisableSpendWithoutHardware\x1f"
    "\x1f"
    "eid=01HRABC456,n=7";
  cr_assert_eq((size_t)len, sizeof(expected) - 1, "Payload length mismatch: got %d, expected %zu",
               len, sizeof(expected) - 1);
  cr_util_cmp_buffers(buf, expected, (size_t)len);
}

Test(sap_payload, buffer_too_small_returns_error) {
  sap_pending_data_t data = {0};
  data.valid = true;
  strncpy(data.action, "SetRecoveryEmail", sizeof(data.action) - 1);
  strncpy(data.value, "test@example.com", sizeof(data.value) - 1);
  strncpy(data.bindings, "tb=ABC", sizeof(data.bindings) - 1);

  uint8_t buf[10] = {0};
  int len = sap_build_payload(&data, buf, sizeof(buf));
  cr_assert_eq(len, -1, "Should fail with small buffer");
}

// ---------------------------------------------------------------------------
// Unit Separator Injection Prevention
// ---------------------------------------------------------------------------

Test(sap_payload, reject_invalid_data) {
  sap_pending_data_t data = {0};
  data.valid = false;
  strncpy(data.action, "SetRecoveryEmail", sizeof(data.action) - 1);
  strncpy(data.value, "test@example.com", sizeof(data.value) - 1);
  strncpy(data.bindings, "tb=ABC", sizeof(data.bindings) - 1);

  uint8_t buf[SAP_PAYLOAD_MAX_SIZE] = {0};
  int len = sap_build_payload(&data, buf, sizeof(buf));
  cr_assert_eq(len, -1, "Should reject data with valid=false");
}

Test(sap_payload, reject_separator_in_action) {
  sap_pending_data_t data = {0};
  data.valid = true;
  data.action[0] = 'A';
  data.action[1] = 'd';
  data.action[2] = 'd';
  data.action[3] = SAP_UNIT_SEPARATOR;
  data.action[4] = 'X';
  data.action[5] = '\0';
  strncpy(data.value, "test", sizeof(data.value) - 1);
  strncpy(data.bindings, "tb=ABC", sizeof(data.bindings) - 1);

  uint8_t buf[SAP_PAYLOAD_MAX_SIZE] = {0};
  int len = sap_build_payload(&data, buf, sizeof(buf));
  cr_assert_eq(len, -1, "Should reject action containing unit separator");
}

Test(sap_payload, reject_separator_in_value) {
  sap_pending_data_t data = {0};
  data.valid = true;
  strncpy(data.action, "SetRecoveryEmail", sizeof(data.action) - 1);
  data.value[0] = 't';
  data.value[1] = 'e';
  data.value[2] = SAP_UNIT_SEPARATOR;
  data.value[3] = 's';
  data.value[4] = 't';
  data.value[5] = '\0';
  strncpy(data.bindings, "tb=ABC", sizeof(data.bindings) - 1);

  uint8_t buf[SAP_PAYLOAD_MAX_SIZE] = {0};
  int len = sap_build_payload(&data, buf, sizeof(buf));
  cr_assert_eq(len, -1, "Should reject value containing unit separator");
}

Test(sap_payload, reject_separator_in_bindings) {
  sap_pending_data_t data = {0};
  data.valid = true;
  strncpy(data.action, "AddRecoveryContact", sizeof(data.action) - 1);
  strncpy(data.value, "Alice", sizeof(data.value) - 1);
  data.bindings[0] = 't';
  data.bindings[1] = 'b';
  data.bindings[2] = '=';
  data.bindings[3] = SAP_UNIT_SEPARATOR;
  data.bindings[4] = '\0';

  uint8_t buf[SAP_PAYLOAD_MAX_SIZE] = {0};
  int len = sap_build_payload(&data, buf, sizeof(buf));
  cr_assert_eq(len, -1, "Should reject bindings containing unit separator");
}
