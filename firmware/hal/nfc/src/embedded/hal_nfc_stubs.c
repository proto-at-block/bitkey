// TODO: Remove all references to LED functions in ST RFAL so that we don't have to define
// these stubs.

#include "rfal_platform.h"

#include <stdint.h>

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
