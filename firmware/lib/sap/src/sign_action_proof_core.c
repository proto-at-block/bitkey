#include "sign_action_proof_core.h"

const char* const sap_action_strings[SAP_ACTION_COUNT] = {
  "SetSpendWithoutHardware",
  "DisableSpendWithoutHardware",
  "SetVerificationThreshold",
  "SetRecoveryEmail",
  "DisableRecoveryEmail",
  "SetRecoveryPhone",
  "DisableRecoveryPhone",
  "SetRecoveryPushNotifications",
  "DisableRecoveryPushNotifications",
  "AddRecoveryContact",
  "RemoveRecoveryContact",
  "AddBeneficiary",
  "RemoveBeneficiary",
  "AcceptRecoveryContactsInvite",
  "AcceptBeneficiariesInvite",
  "CreateLostAppRecovery",
  "CancelLostAppRecovery",
  "DeleteAccount",
  "RotateAppAuthKeys",
  "UpdateDescriptorBackups",
  "RotateSpendingKeyset",
  "EekRestoration",
  "FullAccountCloudBackupRestore",
  "ApproveAppRecovery",
  "StartRecovery",
  "CompleteWallet",
  "UpgradeWallet",
  "CancelLostHardwareRecovery",
  "CancelConflictingRecovery",
  "InitiateWalletUpgrade",
  "RemoveRecoveryCustomer",
  "RemoveBenefactor",
  "KeysetRepairUnseal",
  "KeysetRepairRotate",
  "SetDelayNotifyPeriod",
};

sap_action_t sap_parse_action(const char* str) {
  if (!str) {
    return SAP_ACTION_COUNT;
  }
  for (int i = 0; i < SAP_ACTION_COUNT; i++) {
    if (strcmp(str, sap_action_strings[i]) == 0) {
      return (sap_action_t)i;
    }
  }
  return SAP_ACTION_COUNT;
}

static bool sap_contains_separator(const char* str) {
  for (const char* p = str; *p != '\0'; p++) {
    if ((uint8_t)*p == SAP_UNIT_SEPARATOR) {
      return true;
    }
  }
  return false;
}

// Canonical payload format: ACTIONPROOF<sep>1<sep>action<sep>value<sep>bindings
int sap_build_payload(const sap_pending_data_t* data, uint8_t* buf, size_t buf_size) {
  if (!data->valid) {
    return -1;
  }
  if (sap_contains_separator(data->action) || sap_contains_separator(data->value) ||
      sap_contains_separator(data->bindings)) {
    return -1;
  }

  size_t offset = 0;

#define SAP_APPEND(ptr, len)            \
  do {                                  \
    if (offset + (len) > buf_size)      \
      return -1;                        \
    memcpy(&buf[offset], (ptr), (len)); \
    offset += (len);                    \
  } while (0)

#define SAP_APPEND_SEP()                \
  do {                                  \
    if (offset + 1 > buf_size)          \
      return -1;                        \
    buf[offset++] = SAP_UNIT_SEPARATOR; \
  } while (0)

  SAP_APPEND(SAP_CANONICAL_MAGIC, strlen(SAP_CANONICAL_MAGIC));
  SAP_APPEND_SEP();
  SAP_APPEND(SAP_CANONICAL_VERSION, strlen(SAP_CANONICAL_VERSION));
  SAP_APPEND_SEP();
  SAP_APPEND(data->action, strlen(data->action));
  SAP_APPEND_SEP();
  SAP_APPEND(data->value, strlen(data->value));
  SAP_APPEND_SEP();
  SAP_APPEND(data->bindings, strlen(data->bindings));

#undef SAP_APPEND
#undef SAP_APPEND_SEP

  return (int)offset;
}

void sap_session_init(sap_session_t* session) {
  memset(session, 0, sizeof(sap_session_t));
}
