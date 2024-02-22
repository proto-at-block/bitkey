#pragma once

#include <stdint.h>

#define MAX17262_OCVTABLE_SIZE 16
#define MAX17262_XTABLE_SIZE   16
#define MAX17262_VFSOC_MAX     25600

typedef struct {
  uint16_t DesignCap;
  uint16_t dPacc;
  uint16_t dQacc;
  uint16_t ICHGTerm;
  uint16_t learncfg;
  uint16_t misccfg;
  uint16_t QRTable00;
  uint16_t QRTable10;
  uint16_t QRTable20;
  uint16_t QRTable30;
  uint16_t RCOMP0;
  uint16_t relaxcfg;
  uint16_t TempCo;
  uint16_t Vempty;
  uint16_t OCVTable[MAX17262_OCVTABLE_SIZE];
  uint16_t XTable[MAX17262_XTABLE_SIZE];
} max17262_modelgauge_t;
