#pragma once

#include "printf.h"
#include "st_errno.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

// This file is provided by ST for porting ST RFAL to different platforms.

typedef ReturnCode st_ret_t;

#define ST25R_INT_PORT nfc_config.irq.port
#define ST25R_INT_PIN  nfc_config.irq.pin

#define IRQ_ST25R_EXTI_IRQn GPIO_EVEN_IRQn

#define platformProtectST25RComm()   nop()
#define platformUnprotectST25RComm() nop()

#define platformProtectST25RIrqStatus()   platformProtectST25RComm()
#define platformUnprotectST25RIrqStatus() platformUnprotectST25RComm()

#define platformProtectWorker()      /* Protect RFAL Worker/Task/Process from concurrent execution on \
                                        multi thread platforms   */
#define platformUnprotectWorker()    /* Unprotect RFAL Worker/Task/Process from concurrent execution \
                                        on multi thread platforms */

#define platformIrqST25RSetCallback(cb)
#define platformIrqST25RPinInitialize()

#define platformLedsInitialize()

#define platformLedOff(port, pin)
#define platformLedOn(port, pin)
#define platformLedToogle(port, pin)

#define platformGpioSet(port, pin) \
  mcu_gpio_set(port, pin) /* Turns the given GPIO High                   */
#define platformGpioClear(port, pin) \
  mcu_gpio_clear(port, pin) /* Turns the given GPIO Low                    */

#define platformGpioToogle(port, pin)

#define platformGpioIsHigh(port, pin) \
  (mcu_gpio_read(port, pin) > 0) /* Checks if the given LED is High             */
#define platformGpioIsLow(port, pin) \
  (mcu_gpio_read(port, pin) == 0) /* Checks if the given LED is Low              */

#define platformTimerCreate(t)    (0)
#define platformTimerIsExpired(t) (true)
#define platformTimerDestroy(t)   (0)

#define platformDelay(t)

#define platformGetSysTick()

#define platformErrorHandle() ASSERT(false) /* Global error handler or trap                */

extern bool st_i2c_blocking_send(const uint8_t* tx_buf, uint16_t len, bool last, bool tx_only);
extern bool st_i2c_blocking_recv(const uint8_t* rx_buf, uint16_t len);

#define platformI2CTx(txBuf, len, last, txOnly) st_i2c_blocking_send(txBuf, len, last, txOnly)
#define platformI2CRx(rxBuf, len)               st_i2c_blocking_recv(rxBuf, len)
#define platformI2CStart()
#define platformI2CStop()
#define platformI2CRepeatStart()
#define platformI2CSlaveAddrWR(add)
#define platformI2CSlaveAddrRD(add)

#define platformLog(...) printf(__VA_ARGS__)

/*
******************************************************************************
* RFAL FEATURES CONFIGURATION
******************************************************************************
*/

#define RFAL_FEATURE_LISTEN_MODE \
  true /* Enable/Disable RFAL support for Listen Mode                               */

// TODO turn this off
#define RFAL_FEATURE_WAKEUP_MODE \
  true /* Enable/Disable RFAL support for the Wake-Up mode                          */
#define RFAL_FEATURE_LOWPOWER_MODE \
  false /* Enable/Disable RFAL support for the Low Power mode                        */
#define RFAL_FEATURE_NFCA \
  true /* Enable/Disable RFAL support for NFC-A (ISO14443A)                         */
#define RFAL_FEATURE_NFCB \
  false /* Enable/Disable RFAL support for NFC-B (ISO14443B)                         */
#define RFAL_FEATURE_NFCF \
  false /* Enable/Disable RFAL support for NFC-F (FeliCa)                            */
#define RFAL_FEATURE_NFCV \
  false /* Enable/Disable RFAL support for NFC-V (ISO15693)                          */
#define RFAL_FEATURE_T1T \
  false /* Enable/Disable RFAL support for T1T (Topaz)                               */
#define RFAL_FEATURE_T2T \
  false /* Enable/Disable RFAL support for T2T                                       */
#define RFAL_FEATURE_T4T \
  true /* Enable/Disable RFAL support for T4T                                       */
#define RFAL_FEATURE_ST25TB \
  false /* Enable/Disable RFAL support for ST25TB                                    */
#define RFAL_FEATURE_ST25xV \
  false /* Enable/Disable RFAL support for  ST25TV/ST25DV                            */
#define RFAL_FEATURE_DYNAMIC_ANALOG_CONFIG \
  false /* Enable/Disable Analog Configs to be dynamically updated (RAM)             */
#define RFAL_FEATURE_DPO \
  false /* Enable/Disable RFAL Dynamic Power Output support                          */
#define RFAL_FEATURE_ISO_DEP \
  true /* Enable/Disable RFAL support for ISO-DEP (ISO14443-4)                      */
#define RFAL_FEATURE_ISO_DEP_POLL \
  false /* Enable/Disable RFAL support for Poller mode (PCD) ISO-DEP (ISO14443-4)    */
#define RFAL_FEATURE_ISO_DEP_LISTEN \
  true /* Enable/Disable RFAL support for Listen mode (PICC) ISO-DEP (ISO14443-4)   */
#define RFAL_FEATURE_NFC_DEP \
  false /* Enable/Disable RFAL support for NFC-DEP (NFCIP1/P2P)                      */

#define RFAL_FEATURE_ISO_DEP_IBLOCK_MAX_LEN \
  256U /* ISO-DEP I-Block max length. Please use values as defined by rfalIsoDepFSx */
#define RFAL_FEATURE_NFC_DEP_BLOCK_MAX_LEN \
  254U /* NFC-DEP Block/Payload length. Allowed values: 64, 128, 192, 254           */
#define RFAL_FEATURE_NFC_RF_BUF_LEN \
  258U /* RF buffer length used by RFAL NFC layer                                   */

// TODO Consider changing to large size, up to 4096
#define RFAL_FEATURE_ISO_DEP_APDU_MAX_LEN \
  512U /* ISO-DEP APDU max length. Please use multiples of I-Block max length       */
#define RFAL_FEATURE_NFC_DEP_PDU_MAX_LEN \
  512U /* NFC-DEP PDU max length.                                                   */

#define RFAL_ANALOG_CONFIG_CUSTOM
