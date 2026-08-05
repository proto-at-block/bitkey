#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

typedef enum {
  SAP_ACTION_SET_SPEND_WITHOUT_HARDWARE = 0,
  SAP_ACTION_DISABLE_SPEND_WITHOUT_HARDWARE,
  SAP_ACTION_SET_VERIFICATION_THRESHOLD,
  SAP_ACTION_SET_RECOVERY_EMAIL,
  SAP_ACTION_DISABLE_RECOVERY_EMAIL,
  SAP_ACTION_SET_RECOVERY_PHONE,
  SAP_ACTION_DISABLE_RECOVERY_PHONE,
  SAP_ACTION_SET_RECOVERY_PUSH_NOTIFICATIONS,
  SAP_ACTION_DISABLE_RECOVERY_PUSH_NOTIFICATIONS,
  SAP_ACTION_ADD_RECOVERY_CONTACT,
  SAP_ACTION_REMOVE_RECOVERY_CONTACT,
  SAP_ACTION_ADD_BENEFICIARY,
  SAP_ACTION_REMOVE_BENEFICIARY,
  SAP_ACTION_ACCEPT_RECOVERY_CONTACTS_INVITE,
  SAP_ACTION_ACCEPT_BENEFICIARIES_INVITE,
  SAP_ACTION_CREATE_LOST_APP_RECOVERY,
  SAP_ACTION_CANCEL_LOST_APP_RECOVERY,
  SAP_ACTION_DELETE_ACCOUNT,
  SAP_ACTION_ROTATE_APP_AUTH_KEYS,
  SAP_ACTION_UPDATE_DESCRIPTOR_BACKUPS,
  SAP_ACTION_ROTATE_SPENDING_KEYSET,
  SAP_ACTION_EEK_RESTORATION,
  SAP_ACTION_FULL_ACCOUNT_CLOUD_BACKUP_RESTORE,
  SAP_ACTION_APPROVE_APP_RECOVERY,
  SAP_ACTION_START_RECOVERY,
  SAP_ACTION_COMPLETE_WALLET,
  SAP_ACTION_UPGRADE_WALLET,
  SAP_ACTION_CANCEL_LOST_HARDWARE_RECOVERY,
  SAP_ACTION_CANCEL_CONFLICTING_RECOVERY,
  SAP_ACTION_INITIATE_WALLET_UPGRADE,
  SAP_ACTION_REMOVE_RECOVERY_CUSTOMER,
  SAP_ACTION_REMOVE_BENEFACTOR,
  SAP_ACTION_KEYSET_REPAIR_UNSEAL,
  SAP_ACTION_KEYSET_REPAIR_ROTATE,
  SAP_ACTION_COUNT,
} sap_action_t;

extern const char* const sap_action_strings[SAP_ACTION_COUNT];

sap_action_t sap_parse_action(const char* str);

typedef struct {
  bool valid;
  uint32_t version;
  char action[64];
  char value[128];
  char bindings[256];
} sap_pending_data_t;

#define SAP_PAYLOAD_MAX_SIZE  600
#define SAP_SIG_SIZE          64
#define SAP_UNIT_SEPARATOR    0x1F
#define SAP_CANONICAL_MAGIC   "ACTIONPROOF"
#define SAP_CANONICAL_VERSION "1"

typedef struct {
  sap_pending_data_t pending_data;
  uint8_t payload_buffer[SAP_PAYLOAD_MAX_SIZE];
  uint8_t signature[SAP_SIG_SIZE];
  bool signed_ok;
  bool sign_attempted;
  int sign_result;
} sap_session_t;

int sap_build_payload(const sap_pending_data_t* data, uint8_t* buf, size_t buf_size);
void sap_session_init(sap_session_t* session);

// Implemented in sap_sign.c (firmware-only, requires crypto/wallet deps).
// Builds canonical payload, hashes, signs with HW auth key.
// Populates session->sign_attempted, sign_result, signed_ok, and signature.
int sap_sign(sap_session_t* session);
