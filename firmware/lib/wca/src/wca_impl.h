#pragma once
#include "iso7186.h"
#include "mempool.h"
#include "pb.h"
#include "wallet.pb.h"
#include "wca.h"

#include <stdint.h>

// Unit tests / fuzz builds are not tied to the actual protos
// we use in firmware. This lets us test large protos, even if we don't
// actually need them, without bloating the max proto size in firmware.
#ifdef EMBEDDED_BUILD
#define RESPONSE_BUFFER_SIZE fwpb_wallet_rsp_size
#define COMMAND_BUFFER_SIZE  fwpb_wallet_cmd_size
#else
#include "test.pb.h"
#define RESPONSE_BUFFER_SIZE fwpb_test_rsp_size
#define COMMAND_BUFFER_SIZE  fwpb_test_cmd_size
#endif

typedef struct {
  mempool_t* mempool;
  struct {
    uint8_t buffer[RESPONSE_BUFFER_SIZE];
    uint32_t offset;
    uint32_t size;
    rtos_semaphore_t response_ready;
    wca_sem_take_t sem_take;
    wca_sem_give_t sem_give;
  } encoded_proto_rsp_ctx;  // Holds an *encoded* proto, to be sent as a response from
                            // firmware->app.
  struct {
    uint8_t buffer[COMMAND_BUFFER_SIZE];
    uint32_t offset;
    uint32_t size;
    pb_size_t tag;
  } encoded_proto_cmd_ctx;
} wca_priv_t;

bool wca_version(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len);
bool wca_proto(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len);
bool wca_proto_cont(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len);
bool wca_get_response(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len);

void drain_response_buffer(uint8_t* rsp, uint32_t* rsp_len);
