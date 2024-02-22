#include "FuzzedDataProvider.h"

extern "C" {
#include "fff.h"
#include "rfal_nfc.h"
#include "rfal_platform.h"

#include <stdio.h>

/* rfal_isoDep.c is copied from third-party to our posix dir
 * this is so we can change "static rfalIsoDep gIsoDep;" to "rfalIsoDep gIsoDep;" */

/* START: taken from rfal_isoDep.c */

/*! Enumeration of the IsoDep roles */
typedef enum {
  ISODEP_ROLE_PCD, /*!< Perform as Reader/PCD          */
  ISODEP_ROLE_PICC /*!< Perform as Card/PICC           */
} rfalIsoDepRole;

/*! ISO-DEP layer states */
typedef enum {
  ISODEP_ST_IDLE,         /*!< Idle State                     */
  ISODEP_ST_PCD_TX,       /*!< PCD Transmission State         */
  ISODEP_ST_PCD_RX,       /*!< PCD Reception State            */
  ISODEP_ST_PCD_WAIT_DSL, /*!< PCD Wait for DSL response      */

  ISODEP_ST_PICC_ACT_ATS,    /*!< PICC has replied to RATS (ATS) */
  ISODEP_ST_PICC_ACT_ATTRIB, /*!< PICC has replied to ATTRIB     */
  ISODEP_ST_PICC_RX,         /*!< PICC Reception State           */
  ISODEP_ST_PICC_SWTX,       /*!< PICC Waiting Time eXtension    */
  ISODEP_ST_PICC_SDSL,       /*!< PICC S(DSL) response ongoing   */
  ISODEP_ST_PICC_TX,         /*!< PICC Transmission State        */

  ISODEP_ST_PCD_ACT_RATS, /*!< PCD activation (RATS)          */
  ISODEP_ST_PCD_ACT_PPS,  /*!< PCD activation (PPS)           */

} rfalIsoDepState;

#define ISODEP_CONTROLMSG_BUF_LEN (4U)
/*! Holds all ISO-DEP data(counters, buffers, ID, timeouts, frame size)         */
typedef struct {
  rfalIsoDepState state; /*!< ISO-DEP module state                      */
  rfalIsoDepRole role;   /*!< Current ISO-DEP role                      */

  uint8_t blockNumber;   /*!< Current block number                      */
  uint8_t did;           /*!< Current DID                               */
  uint8_t nad;           /*!< Current DID                               */
  uint8_t cntIRetrys;    /*!< I-Block retry counter                     */
  uint8_t cntRRetrys;    /*!< R-Block retry counter                     */
  uint8_t cntSDslRetrys; /*!< S(DESELECT) retry counter                 */
  uint8_t cntSWtxRetrys; /*!< Overall S(WTX) retry counter              */
  uint8_t cntSWtxNack;   /*!< R(NACK) answered with S(WTX) counter      */
  uint32_t fwt;          /*!< Current FWT (Frame Waiting Time)          */
  uint32_t dFwt;         /*!< Current delta FWT                         */
  uint16_t fsx;          /*!< Current FSx FSC or FSD (max Frame size)   */
  bool isTxChaining;     /*!< Flag for chaining on Tx                   */
  bool isRxChaining;     /*!< Flag for chaining on Rx                   */
  uint8_t* txBuf;        /*!< Tx buffer pointer                         */
  uint8_t* rxBuf;        /*!< Rx buffer pointer                         */
  uint16_t txBufLen;     /*!< Tx buffer length                          */
  uint16_t rxBufLen;     /*!< Rx buffer length                          */
  uint8_t txBufInfPos;   /*!< Start of payload in txBuf                 */
  uint8_t rxBufInfPos;   /*!< Start of payload in rxBuf                 */

  uint16_t ourFsx;   /*!< Our current FSx FSC or FSD (Frame size)   */
  uint8_t lastPCB;   /*!< Last PCB sent                             */
  uint8_t lastWTXM;  /*!< Last WTXM sent                            */
  uint8_t atsTA;     /*!< TA on ATS                                 */
  uint8_t hdrLen;    /*!< Current ISO-DEP length                    */
  rfalBitRate txBR;  /*!< Current Tx Bit Rate                       */
  rfalBitRate rxBR;  /*!< Current Rx Bit Rate                       */
  uint16_t* rxLen;   /*!< Output parameter ptr to Rx length         */
  bool* rxChaining;  /*!< Output parameter ptr to Rx chaining flag  */
  uint32_t WTXTimer; /*!< Timer used for WTX                        */
  bool lastDID00;    /*!< Last PCD block had DID flag (for DID = 0) */

  bool isTxPending; /*!< Flag pending Block while waiting WTX Ack  */
  bool isWait4WTX;  /*!< Flag for waiting WTX Ack                  */

  uint8_t maxRetriesI;     /*!< Number of retries for a I-Block           */
  uint8_t maxRetriesR;     /*!< Number of retries for a R-Block           */
  uint8_t maxRetriesSDSL;  /*!< Number of retries for S(DESELECT) errors  */
  uint8_t maxRetriesSWTX;  /*!< Number of retries for S(WTX) errors       */
  uint8_t maxRetriesSnWTX; /*!< Number of retries S(WTX) replied w NACK  */
  uint8_t maxRetriesRATS;  /*!< Number of retries for RATS                */

  rfalComplianceMode compMode; /*!< Compliance mode                           */

  uint8_t ctrlBuf[ISODEP_CONTROLMSG_BUF_LEN]; /*!< Control msg buf   */
  uint16_t ctrlRxLen;                         /*!< Control msg rcvd len                         */

  union { /*  PRQA S 0750 # MISRA 19.2 - Members of the union will not be used concurrently, only
             one frame at a time */
#if RFAL_FEATURE_NFCA
    rfalIsoDepRats ratsReq;
    rfalIsoDepPpsReq ppsReq;
#endif /* RFAL_FEATURE_NFCA */

#if RFAL_FEATURE_NFCB
    rfalIsoDepAttribCmd attribReq;
#endif    /* RFAL_FEATURE_NFCB */
  } actv; /*!< Activation buffer              */

  uint8_t* rxLen8;                     /*!< Receive length (8-bit)         */
  rfalIsoDepDevice* actvDev;           /*!< Activation Device Info         */
  rfalIsoDepListenActvParam actvParam; /*!< Listen Activation context      */

  rfalIsoDepApduTxRxParam APDUParam; /*!< APDU TxRx params               */
  uint16_t APDUTxPos;                /*!< APDU Tx position               */
  uint16_t APDURxPos;                /*!< APDU Rx position               */
  bool isAPDURxChaining;             /*!< APDU Transceive chaining flag  */

} rfalIsoDep;

/* END: taken from rfal_isoDep.c */

extern rfalIsoDep gIsoDep;

bool nop(void) {
  return true;
}

ReturnCode rfalGetTransceiveStatus(void) {
  return ERR_NONE;  // needed to make progress in rfalIsoDepDataExchangePICC
}

ReturnCode rfalInitialize(void) {
  return ERR_NONE;
}

bool rfalIsTransceiveInTx(void) {
  return false;
}

ReturnCode rfalSetBitRate(rfalBitRate txBR, rfalBitRate rxBR) {
  return ERR_NONE;
}

void rfalSetErrorHandling(rfalEHandling eHandling) {
  return;
}

void rfalSetGT(uint32_t GT) {
  return;
}

ReturnCode rfalSetMode(rfalMode mode, rfalBitRate txBR, rfalBitRate rxBR) {
  return ERR_NONE;
}

ReturnCode rfalStartTransceive(const rfalTransceiveContext* ctx) {
  return ERR_NONE;
}

ReturnCode rfalTransceiveBlockingTx(uint8_t* txBuf, uint16_t txBufLen, uint8_t* rxBuf,
                                    uint16_t rxBufLen, uint16_t* actLen, uint32_t flags,
                                    uint32_t fwt) {
  return ERR_NONE;
}

ReturnCode rfalTransceiveBlockingTxRx(uint8_t* txBuf, uint16_t txBufLen, uint8_t* rxBuf,
                                      uint16_t rxBufLen, uint16_t* actLen, uint32_t flags,
                                      uint32_t fwt) {
  return ERR_NONE;
}
}

DEFINE_FFF_GLOBALS;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  rfalIsoDepInitialize();

  gIsoDep.ourFsx = 256;    // current frame size, value taken from W1
  gIsoDep.rxBufLen = 259;  // max length of rx buffer, value taken from W1

  // min size 2 since 0 or 1 leads to an OOB read, but this seems fine since rxBufLen is much larger
  // max size of up to rxBufLen was confirmed using nfcpy
  uint16_t rx_len = fuzzed_data.ConsumeIntegralInRange<uint16_t>(2, gIsoDep.rxBufLen);
  uint8_t* rx_buf = (uint8_t*)malloc((size_t)rx_len);
  rx_len = fuzzed_data.ConsumeData(rx_buf, rx_len);

  // min bound is not guaranteed since FuzzedDataProvider is best-effort
  if (rx_len < 2) {
    free(rx_buf);
    return 1;
  }

  gIsoDep.rxLen = &rx_len;
  gIsoDep.rxBuf = rx_buf;

  bool rx_chaining = false;
  gIsoDep.rxChaining = &rx_chaining;
  gIsoDep.actvParam.isRxChaining = &rx_chaining;

  gIsoDep.role = ISODEP_ROLE_PICC;
  gIsoDep.state = ISODEP_ST_PICC_RX;

  gIsoDep.isTxPending = fuzzed_data.ConsumeBool();
  gIsoDep.isTxChaining = fuzzed_data.ConsumeBool();

  gIsoDep.fwt = 67108864;  // Frame Waiting Time, this value is copied from W1
  gIsoDep.blockNumber = fuzzed_data.ConsumeIntegral<uint8_t>();
  gIsoDep.lastPCB = fuzzed_data.ConsumeIntegral<uint8_t>();
  gIsoDep.lastWTXM = fuzzed_data.ConsumeIntegral<uint8_t>();
  gIsoDep.did = fuzzed_data.ConsumeIntegral<uint8_t>();

  //  trigger rfalIsoDepDataExchangePICC
  rfalIsoDepGetTransceiveStatus();

  free(rx_buf);

  return 0;
}
