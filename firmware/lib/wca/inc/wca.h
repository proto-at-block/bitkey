#pragma once

#include "mempool.h"

#include <stdbool.h>
#include <stdint.h>

// Wallet Custom APDU (WCA) protocol functions.
// See README for details.

#define WCA_CLA              (0x87)
#define WCA_INS_VERSION      (0x74)
#define WCA_INS_PROTO        (0x75)
#define WCA_INS_PROTO_CONT   (0x77)
#define WCA_INS_GET_RESPONSE (0x78)

// APDU size constraints (see README.md for details)
// APDU_OVERHEAD = CLA (1) + INS (1) + P1 (1) + P2 (1) + Lc (1 or 3)
#define WCA_MAX_BUFFER_SIZE (512)
#define WCA_APDU_OVERHEAD   (7)
#define WCA_MAX_PROTO_SIZE  (WCA_MAX_BUFFER_SIZE - WCA_APDU_OVERHEAD)

typedef bool (*wca_sem_take_t)(void);
typedef bool (*wca_sem_give_t)(void);
typedef struct {
  mempool_t* mempool;
  wca_sem_take_t sem_take;
  wca_sem_give_t sem_give;
} wca_api_t;

void wca_init(wca_api_t* api);

bool wca_handle_command(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len);
bool wca_is_valid(uint8_t* cmd, uint32_t cmd_len);

bool wca_proto(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len);

void wca_notify_proto_ready(void);
