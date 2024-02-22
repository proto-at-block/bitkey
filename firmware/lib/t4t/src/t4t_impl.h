#pragma once

#include "iso7186.h"

// TODO: We don't actually want to use NDEF, we want to just use NDEF APIs
// for firmware update. Remove the actual NDEF buffer here and replace with hooks
// into firmware update code.
// Fake NDEF file size to support maximum transfer sizes
#define FAKE_NDEF_SIZE 0xFFFE
#define REAL_NDEF_SIZE 0x800

// CC file ID
#define CC_FID 0xE103
// NDEF file ID
#define NDEF_FID 0xE104

// Emulated files
#define NUM_FILES (2)
enum { FILE_IDX_NONE = 999, FILE_IDX_CC = 0, FILE_IDX_NDEF = 1 };

typedef struct {
  struct {
    uint16_t cc_len;  // Size of cc file
    uint8_t t4t_vno;  // T4T mapping version
    uint16_t mle;     // Maximum data size readable with a single READ_BINARY
    uint16_t mlc;     // Maximum data size that can be sent in a single command
    // NDEF file control TLV
    struct {
      uint8_t type;
      uint8_t length;
      uint16_t ndef_file_id;
      uint16_t ndef_file_size;
      uint8_t read_access_condition;
      uint8_t write_access_condition;
    } __attribute__((__packed__)) ndef_file_ctrl;
  } __attribute__((__packed__)) cc_file;
  uint8_t ndef_file[REAL_NDEF_SIZE];  // TODO See FWUP comment above.
  uint32_t file_index;
  uint32_t file_sizes[NUM_FILES];
} t4t_priv_t;

extern t4t_priv_t t4t_priv;

#define T4T_APDU_FIELD_NOT_SET (-1)

#define T4T_CLA            (0x00)
#define T4T_INS_SELECT     (0xA4)
#define T4T_INS_READ       (0xB0)
#define T4T_INS_UPDATE     (0xD6)
#define T4T_INS_UPDATE_ODO (0xD7)

uint8_t* get_current_file(void);
