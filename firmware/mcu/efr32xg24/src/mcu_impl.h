#pragma once

#include "em_bus.h"

// Hide Gecko SDK naming conventions to ease future porting.

#define reg_read_bit     BUS_RegBitRead
#define reg_masked_write BUS_RegMaskedWrite
