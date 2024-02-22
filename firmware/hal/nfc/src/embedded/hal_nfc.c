#include "assert.h"
#include "exti.h"
#include "hal_nfc_timer.h"
#include "hex.h"
#include "log.h"
#include "mcu_i2c_opt.h"
#include "rfal_isoDep.h"
#include "rfal_nfc.h"
#include "rfal_rf.h"
#include "st25r3916_irq.h"
#include "t4t.h"
#include "wca.h"

#include <string.h>

#define HAL_NFC_LOG_COMMS (0)

extern nfc_config_t nfc_config;

static struct {
  rfalNfcDiscoverParam discovery_cfg;
  bool transfer_in_progress;
  uint32_t transfer_timeout_ms;
  exti_config_t irq_cfg;
} nfc_priv NFC_TASK_DATA = {
  .discovery_cfg = {0},
  .transfer_in_progress = false,
  .transfer_timeout_ms = 500,
};

// ST25 configuration fields.
static uint8_t NFCID3[] = {0x01, 0xFE, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A};
static uint8_t GB[] = {0x46, 0x66, 0x6d, 0x01, 0x01, 0x11, 0x02, 0x02, 0x07, 0x80,
                       0x03, 0x02, 0x00, 0x03, 0x04, 0x01, 0x32, 0x07, 0x01, 0x03};
static uint8_t ceNFCA_NFCID[] = {'W', '1', 'A', '0'};  // NFCID1 / UID (4 bytes)
static uint8_t ceNFCA_SENS_RES[] = {0x02, 0x00};       // SENS_RES / ATQA for 4-byte UID
static uint8_t ceNFCA_SEL_RES = 0x20;                  // SEL_RES / SAK

static void nfc_i2c_init(void) {
  // Configure interrupts
  nfc_priv.irq_cfg.gpio.mode = MCU_GPIO_MODE_INPUT;
  nfc_priv.irq_cfg.gpio.port = nfc_config.irq.port;
  nfc_priv.irq_cfg.gpio.pin = nfc_config.irq.pin;
  nfc_priv.irq_cfg.trigger = EXTI_TRIGGER_RISING;
  exti_enable(&nfc_priv.irq_cfg);

  mcu_i2c_bus_init(nfc_config.i2c.bus, nfc_config.i2c.device, true);
}

static bool filter_comand(uint8_t* buffer, uint32_t len) {
  return (wca_is_valid(buffer, len)) || (t4t_is_valid(buffer, len));
}

static void nfc_st25_init(void) {
  st_ret_t err = rfalNfcInitialize();
  ASSERT_LOG(err == RFAL_ERR_NONE, "%d", err);

  nfc_priv.discovery_cfg.compMode = RFAL_COMPLIANCE_MODE_NFC;
  nfc_priv.discovery_cfg.devLimit = 1U;
  nfc_priv.discovery_cfg.nfcfBR = RFAL_BR_212;
  nfc_priv.discovery_cfg.ap2pBR = RFAL_BR_424;
  nfc_priv.discovery_cfg.maxBR = RFAL_BR_KEEP;

  nfc_priv.discovery_cfg.isoDepFS = RFAL_ISODEP_FSXI_256;
  nfc_priv.discovery_cfg.nfcDepLR = RFAL_NFCDEP_LR_254;
  memcpy(&nfc_priv.discovery_cfg.nfcid3, NFCID3, sizeof(NFCID3));
  memcpy(&nfc_priv.discovery_cfg.GB, GB, sizeof(GB));
  nfc_priv.discovery_cfg.GBLen = sizeof(GB);
  nfc_priv.discovery_cfg.p2pNfcaPrio = false;

  nfc_priv.discovery_cfg.notifyCb = NULL;
  nfc_priv.discovery_cfg.wakeupEnabled = false;
  nfc_priv.discovery_cfg.wakeupConfigDefault = true;
  nfc_priv.discovery_cfg.wakeupNPolls = 1U;
  nfc_priv.discovery_cfg.totalDuration = 1000U;
  nfc_priv.discovery_cfg.techs2Find = RFAL_NFC_LISTEN_TECH_A;

  memcpy(nfc_priv.discovery_cfg.lmConfigPA.SENS_RES, ceNFCA_SENS_RES, RFAL_LM_SENS_RES_LEN);
  memcpy(nfc_priv.discovery_cfg.lmConfigPA.nfcid, ceNFCA_NFCID, RFAL_LM_NFCID_LEN_04);
  nfc_priv.discovery_cfg.lmConfigPA.nfcidLen = RFAL_LM_NFCID_LEN_04;
  nfc_priv.discovery_cfg.lmConfigPA.SEL_RES = ceNFCA_SEL_RES;

  // Check for valid configuration by calling discover once
  err = rfalNfcDiscover(&nfc_priv.discovery_cfg);
  rfalNfcDeactivate(RFAL_NFC_DEACTIVATE_IDLE);
  ASSERT_LOG(err == RFAL_ERR_NONE, "%d", err);
}

void hal_nfc_init(rtos_timer_callback_t timer_callback) {
  nfc_timer_init(timer_callback);  // Timers used by ST-RFAL
  nfc_i2c_init();
  nfc_st25_init();
}

void hal_nfc_wfi(void) {
  exti_wait(&nfc_priv.irq_cfg, RTOS_EVENT_GROUP_TIMEOUT_MAX, true);
}

void hal_nfc_handle_interrupts(void) {
  st25r3916Isr();
}

void hal_nfc_worker(hal_nfc_callback callback) {
  rfalNfcWorker();

  static uint8_t* NFC_TASK_DATA rx_buf = NULL;
  static uint16_t* NFC_TASK_DATA rx_len = 0;

  int result = 0;
  switch (rfalNfcGetState()) {
    case RFAL_NFC_STATE_IDLE:
      result = rfalNfcDiscover(&nfc_priv.discovery_cfg);
      if (result != RFAL_ERR_NONE) {
        ASSERT_LOG(false, "%d", result);
      }
      break;

    case RFAL_NFC_STATE_ACTIVATED:
      result = rfalNfcDataExchangeStart(NULL, 0, &rx_buf, &rx_len, 0);
      if (result != RFAL_ERR_NONE) {
        ASSERT_LOG(false, "%d", result);
      }
      (void)rfalNfcDataExchangeGetStatus();

      // This is a REALLY gross Android compatibility hack.
      //
      // We've observed that Android phones, when background tag reading, can hit this code path.
      //
      // Android phones seem (?) to inititate background tag reading with a vendor-specific byte
      // (0xe0 on Samsung, 0xa0 on Pixel).
      // And for some reason, when background tag reading happens, we get stuck in
      // RFAL_NFC_STATE_DATAEXCHANGE and can never resume until the field is torn (or we
      // deactivate it). This means that NFC won't work if you initiate an NFC transaction through
      // the Bitkey app after the field was established due to background tag reading. It also means
      // that successive NFC transactions won't work unless you tear the field and put the phone
      // back next to the hardware.
      //
      // The "fix" is to deactivate the field ourselves. This won't impact regular NFC
      // transactions through the app, since background tag reading doesn't happen when the
      // foreground app is doing an NFC transaction.
      //
      // Note also that we have to allow t4t commands (which we don't actually properly handle, but
      // do respond to) because otherwise NFC on iOS doesn't work.
      if (!filter_comand(rx_buf, *rx_len)) {
#if HAL_NFC_LOG_COMMS
        LOGD("filtered:");
        dumphex(rx_buf, *rx_len);
#endif
        rfalNfcDeactivate(RFAL_NFC_DEACTIVATE_IDLE);
      }

      break;

    case RFAL_NFC_STATE_DATAEXCHANGE_DONE: {
      if (RFAL_ERR_NONE != rfalNfcDataExchangeGetStatus()) {
        ASSERT(RFAL_ERR_NONE == rfalNfcDeactivate(RFAL_NFC_DEACTIVATE_IDLE));
        break;
      }

#if HAL_NFC_LOG_COMMS
      if (*rx_len > 0) {
        LOGD("received:");
        dumphex(rx_buf, *rx_len);
      }
#endif

      uint8_t tx_buf[RFAL_FEATURE_ISO_DEP_APDU_MAX_LEN] = {0};
      uint32_t tx_len = sizeof(tx_buf);
      if (callback(rx_buf, *rx_len, tx_buf, &tx_len)) {
        ASSERT(RFAL_ERR_NONE ==
               rfalNfcDataExchangeStart(tx_buf, tx_len, &rx_buf, &rx_len, RFAL_FWT_NONE));
      } else {
        LOGD("Protocol layer failure!");
        rfalNfcDeactivate(RFAL_NFC_DEACTIVATE_IDLE);
      }

#if HAL_NFC_LOG_COMMS
      if (tx_len > 0) {
        LOGD("sending:");
        dumphex(tx_buf, tx_len);
      }
#endif

      break;
    }

    default:
      // Necessary to meet NFC timing requirements
      rfalNfcWorker();
      break;
  }
}

bool st_i2c_blocking_send(const uint8_t* tx_buf, uint16_t len, bool last, bool tx_only) {
  mcu_i2c_transfer_opt_seq_t sequence = {
    .buf = (uint8_t*)tx_buf,
    .len = len,
    .last = last,
    .tx_only = tx_only,
  };
  if (!nfc_priv.transfer_in_progress) {
    mcu_i2c_transfer_state_t state = mcu_i2c_transfer_opt_init(nfc_config.i2c.device, &sequence);
    ASSERT_LOG(state != MCU_I2C_STATE_ERROR, "%d", state);
    nfc_priv.transfer_in_progress = true;
  }

  bool ret = true;
  const uint32_t start = rtos_thread_systime();
  while (!RTOS_DEADLINE(start, nfc_priv.transfer_timeout_ms)) {
    mcu_i2c_transfer_state_t state = mcu_i2c_transfer_opt(nfc_config.i2c.device, &sequence);
    if (state == MCU_I2C_STATE_DONE) {
      nfc_priv.transfer_in_progress = false;
      ret = tx_only;  // If we're done, and we only wanted to transmit -- we are okay.
      break;
    } else if (state == MCU_I2C_STATE_WF_RX_BEGIN) {
      ret = true;  // Okay to resume with st_i2c_blocking_recv().
      break;
    } else if (state == MCU_I2C_STATE_WF_TRANSFER_RESUME) {
      ret = true;  // Okay to resume with st_i2c_blocking_send().
      break;
    }
  }

  return ret;
}

bool st_i2c_blocking_recv(const uint8_t* rx_buf, uint16_t len) {
  mcu_i2c_transfer_opt_seq_t sequence = {
    .buf = (uint8_t*)rx_buf,
    .len = len,
    .last = true,
    .tx_only = false,
  };

  const uint32_t start = rtos_thread_systime();
  while (!RTOS_DEADLINE(start, nfc_priv.transfer_timeout_ms)) {
    if (mcu_i2c_transfer_opt(nfc_config.i2c.device, &sequence) == MCU_I2C_STATE_DONE) {
      break;
    }
  }

  nfc_priv.transfer_in_progress = false;
  return true;
}

// TODO: Remove all references to LED functions in ST RFAL so that we don't have to define
// these stubs.
void st25r3916ledRxOff(void) {}
void st25r3916ledFieldOff(void) {}

void st25r3916ledInit(void) {
  platformLedsInitialize();
  st25r3916ledRxOff();
  st25r3916ledFieldOff();
}

void st25r3916ledEvtIrq(uint32_t irqs) {
  (void)irqs;
}
void st25r3916ledEvtWrReg(uint8_t reg, uint8_t val) {
  (void)reg;
  (void)val;
}
void st25r3916ledEvtWrMultiReg(uint8_t reg, const uint8_t* vals, uint8_t len) {
  (void)reg;
  (void)vals;
  (void)len;
}
void st25r3916ledEvtCmd(uint8_t cmd) {
  (void)cmd;
}
