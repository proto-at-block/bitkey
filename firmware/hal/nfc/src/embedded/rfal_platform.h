#pragma once

#include "assert.h"
#include "hal_nfc.h"
#include "hal_nfc_timer_impl.h"
#include "mcu.h"
#include "platform.h"
#include "printf.h"
#include "rfal_utils.h"
#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#define RFAL_USE_I2C 1

// This file is provided by ST for porting ST RFAL to different platforms.
extern nfc_config_t nfc_config;
typedef ReturnCode st_ret_t;

/*
******************************************************************************
* GLOBAL DEFINES
******************************************************************************
*/

#define ST25R_INT_PORT nfc_config.irq.port
#define ST25R_INT_PIN  nfc_config.irq.pin

#define ST25R_RESET_PIN  ST25R_RST_Pin       /*!< GPIO pin used for ST25R Reset                 */
#define ST25R_RESET_PORT ST25R_RST_GPIO_Port /*!< GPIO port used for ST25R Reset                */

/*
******************************************************************************
* GLOBAL MACROS
******************************************************************************
*/
#define platformProtectST25RComm()   rtos_thread_enter_critical()
#define platformUnprotectST25RComm() rtos_thread_exit_critical()

#define platformProtectST25RIrqStatus()   platformProtectST25RComm()
#define platformUnprotectST25RIrqStatus() platformUnprotectST25RComm()

#define platformLedOff(port, pin)
#define platformLedOn(port, pin)
#define platformLedToggle(port, pin)

#define platformGpioSet(st_port, st_pin) \
  mcu_gpio_set(&(const mcu_gpio_config_t){.port = st_port, .pin = st_pin})
#define platformGpioClear(st_port, st_pin) \
  mcu_gpio_clear(&(const mcu_gpio_config_t){.port = st_port, .pin = st_pin})

#define platformGpioToggle(port, pin)

#define platformGpioIsHigh(st_port, st_pin) \
  (mcu_gpio_read(&(const mcu_gpio_config_t){.port = st_port, .pin = st_pin}) > 0)
#define platformGpioIsLow(st_port, st_pin) \
  (mcu_gpio_read(&(const mcu_gpio_config_t){.port = st_port, .pin = st_pin}) == 0)

#define platformTimerCreate(t)    nfc_timer_create(t)
#define platformTimerIsExpired(t) nfc_timer_expired(t)
#define platformTimerDestroy(t)   nfc_timer_stop(t)
#define platformDelay(t)          rtos_thread_sleep(t)

#define platformGetSysTick() rtos_thread_systime()

#define platformAssert(exp)   ASSERT(exp)   /*!< Asserts whether the given expression is true*/
#define platformErrorHandle() ASSERT(false) /* Global error handler or trap                */

#ifdef RFAL_USE_I2C

extern bool st_i2c_blocking_send(const uint8_t* tx_buf, uint16_t len, bool last, bool tx_only);
extern bool st_i2c_blocking_recv(const uint8_t* rx_buf, uint16_t len);

#define platformI2CTx(txBuf, len, last, txOnly) st_i2c_blocking_send(txBuf, len, last, txOnly)
#define platformI2CRx(rxBuf, len)               st_i2c_blocking_recv(rxBuf, len)
#define platformI2CStart()
#define platformI2CStop()
#define platformI2CRepeatStart()
#define platformI2CSlaveAddrWR(add)
#define platformI2CSlaveAddrRD(add)

#else /* RFAL_USE_I2C */

#define platformSpiSelect() \
  platformGpioClear(ST25R_SS_PORT, ST25R_SS_PIN) /*!< SPI SS\CS: Chip|Slave Select */
#define platformSpiDeselect() \
  platformGpioSet(ST25R_SS_PORT, ST25R_SS_PIN) /*!< SPI SS\CS: Chip|Slave Deselect              */
#define platformSpiTxRx(txBuf, rxBuf, len) \
  spiTxRx(txBuf, rxBuf, len) /*!< SPI transceive                              */

#endif /* RFAL_USE_I2C */

#define platformLog(...) printf(__VA_ARGS__)

/*
******************************************************************************
* GLOBAL VARIABLES
******************************************************************************
*/
extern uint8_t globalCommProtectCnt; /* Global Protection Counter provided per platform -
                                        instantiated in main.c    */

/*
******************************************************************************
* RFAL FEATURES CONFIGURATION
******************************************************************************
*/

#define RFAL_FEATURE_LISTEN_MODE \
  true /* Enable/Disable RFAL support for Listen Mode                                 */

// TODO turn this off
#define RFAL_FEATURE_WAKEUP_MODE \
  true /* Enable/Disable RFAL support for the Wake-Up mode                            */
#define RFAL_FEATURE_LOWPOWER_MODE \
  false /* Enable/Disable RFAL support for the Low Power mode                         */
#if defined(PLATFORM_CFG_NFC_TYPE_A_SUPPORT) && (PLATFORM_CFG_NFC_TYPE_A_SUPPORT)
#define RFAL_FEATURE_NFCA \
  true /* Enable/Disable RFAL support for NFC-A (ISO14443A)                           */
#else
#define RFAL_FEATURE_NFCA \
  false /* Enable/Disable RFAL support for NFC-A (ISO14443A)                          */
#endif
#if defined(PLATFORM_CFG_NFC_TYPE_B_SUPPORT) && (PLATFORM_CFG_NFC_TYPE_B_SUPPORT)
#define RFAL_FEATURE_NFCB \
  true /* Enable/Disable RFAL support for NFC-B (ISO14443B)                           */
#else
#define RFAL_FEATURE_NFCB \
  false /* Enable/Disable RFAL support for NFC-B (ISO14443B)                          */
#endif
#define RFAL_FEATURE_NFCF \
  false /* Enable/Disable RFAL support for NFC-F (FeliCa)                             */
#define RFAL_FEATURE_NFCV \
  false /* Enable/Disable RFAL support for NFC-V (ISO15693)                           */
#define RFAL_FEATURE_T1T \
  false /* Enable/Disable RFAL support for T1T (Topaz)                                */
#define RFAL_FEATURE_T2T \
  false /* Enable/Disable RFAL support for T2T                                        */
#define RFAL_FEATURE_T4T \
  true /* Enable/Disable RFAL support for T4T                                         */
#define RFAL_FEATURE_ST25TB \
  false /* Enable/Disable RFAL support for ST25TB                                     */
#define RFAL_FEATURE_ST25xV \
  false /* Enable/Disable RFAL support for  ST25TV/ST25DV                             */
#define RFAL_FEATURE_DYNAMIC_ANALOG_CONFIG \
  false /* Enable/Disable Analog Configs to be dynamically updated (RAM)              */
#define RFAL_FEATURE_DPO \
  false /* Enable/Disable RFAL Dynamic Power Output support                           */
#define RFAL_FEATURE_ISO_DEP \
  true /* Enable/Disable RFAL support for ISO-DEP (ISO14443-4)                        */
#define RFAL_FEATURE_ISO_DEP_POLL \
  false /* Enable/Disable RFAL support for Poller mode (PCD) ISO-DEP (ISO14443-4)     */
#define RFAL_FEATURE_ISO_DEP_LISTEN \
  true /* Enable/Disable RFAL support for Listen mode (PICC) ISO-DEP (ISO14443-4)     */
#define RFAL_FEATURE_NFC_DEP \
  false /* Enable/Disable RFAL support for NFC-DEP (NFCIP1/P2P)                       */

#define RFAL_FEATURE_ISO_DEP_IBLOCK_MAX_LEN \
  256U /*!< ISO-DEP I-Block max length. Please use values as defined by rfalIsoDepFSx */
#define RFAL_FEATURE_NFC_DEP_BLOCK_MAX_LEN \
  254U /*!< NFC-DEP Block/Payload length. Allowed values: 64, 128, 192, 254           */
#define RFAL_FEATURE_NFC_RF_BUF_LEN \
  258U /*!< RF buffer length used by RFAL NFC layer                                   */

#define RFAL_FEATURE_ISO_DEP_APDU_MAX_LEN \
  512U /*!< ISO-DEP APDU max length. Please use multiples of I-Block max length       */
#define RFAL_FEATURE_NFC_DEP_PDU_MAX_LEN \
  512U /*!< NFC-DEP PDU max length.                                                   */

/*
******************************************************************************
* RFAL CUSTOM SETTINGS
******************************************************************************
  Custom analog configs are used to cope with Automatic Antenna Tuning (AAT)
  that are optimized differently for each board.
*/
#define RFAL_ANALOG_CONFIG_CUSTOM /*!< Use Custom Analog Configs when defined */

#ifndef platformProtectST25RIrqStatus
#define platformProtectST25RIrqStatus()    /*!< Protect unique access to IRQ status var - IRQ disable \
                                              on single thread environment (MCU) ; Mutex lock on a    \
                                              multi thread environment */
#endif                                     /* platformProtectST25RIrqStatus */

#ifndef platformUnprotectST25RIrqStatus
#define platformUnprotectST25RIrqStatus()    /*!< Unprotect the IRQ status var - IRQ enable on a    \
                                                single thread environment (MCU) ; Mutex unlock on a \
                                                multi thread environment         */
#endif                                       /* platformUnprotectST25RIrqStatus */

#ifndef platformProtectWorker
#define platformProtectWorker()    /* Protect RFAL Worker/Task/Process from concurrent execution on \
                                      multi thread platforms   */
#endif                             /* platformProtectWorker */

#ifndef platformUnprotectWorker
#define platformUnprotectWorker()    /* Unprotect RFAL Worker/Task/Process from concurrent execution \
                                        on multi thread platforms */
#endif                               /* platformUnprotectWorker */

#ifndef platformIrqST25RPinInitialize
#define platformIrqST25RPinInitialize() /*!< Initializes ST25R IRQ pin                     */
#endif                                  /* platformIrqST25RPinInitialize */

#ifndef platformIrqST25RSetCallback
#define platformIrqST25RSetCallback(cb) /*!< Sets ST25R ISR callback                       */
#endif                                  /* platformIrqST25RSetCallback */

#ifndef platformLedsInitialize
#define platformLedsInitialize() /*!< Initializes the pins used as LEDs to outputs  */
#endif                           /* platformLedsInitialize */

#ifndef platformLedOff
#define platformLedOff(port, pin) /*!< Turns the given LED Off                       */
#endif                            /* platformLedOff */

#ifndef platformLedOn
#define platformLedOn(port, pin) /*!< Turns the given LED On                        */
#endif                           /* platformLedOn */

#ifndef platformLedToggle
#define platformLedToggle(port, pin) /*!< Toggles the given LED                         */
#endif                               /* platformLedToggle */

#ifndef platformGetSysTick
#define platformGetSysTick() /*!< Get System Tick ( 1 tick = 1 ms)              */
#endif                       /* platformGetSysTick */

#ifndef platformTimerDestroy
#define platformTimerDestroy(timer) /*!< Stops and released the given timer            */
#endif                              /* platformTimerDestroy */

#ifndef platformLog
#define platformLog(...) /*!< Log method                                    */
#endif                   /* platformLog */

#ifndef platformAssert
#define platformAssert(exp) /*!< Asserts whether the given expression is true */
#endif                      /* platformAssert */

#ifndef platformErrorHandle
#define platformErrorHandle() /*!< Global error handler or trap                 */
#endif                        /* platformErrorHandle */

#ifdef __cplusplus
}
#endif
