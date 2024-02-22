#include "battery_configs.h"

#include "assert.h"

// These modelgauge configs are provided by Analog Devices after characterization of the cells
static max17262_modelgauge_t modelgauge[] = {
  [BATTERY_VARIANT_E] =
    {
      .DesignCap = 0x05cc,
      .dPacc = 0x0c80,
      .dQacc = 0x02e6,
      .ICHGTerm = 0x0120,
      .learncfg = 0x4402,
      .misccfg = 0x3830,
      .QRTable00 = 0x1680,
      .QRTable10 = 0x0c80,
      .QRTable20 = 0x0780,
      .QRTable30 = 0x0680,
      .RCOMP0 = 0x0035,
      .relaxcfg = 0x023c,
      .TempCo = 0x193f,
      .Vempty = 0x965a,
      .OCVTable =
        {
          0x9450,
          0xade0,
          0xb740,
          0xb890,
          0xb950,
          0xbb30,
          0xbc80,
          0xbdd0,
          0xbe70,
          0xbf30,
          0xc070,
          0xc270,
          0xc3e0,
          0xc7e0,
          0xcb10,
          0xcc80,
        },
      .XTable =
        {
          0x0090,
          0x0200,
          0x1430,
          0x1d00,
          0x1100,
          0x1600,
          0x1800,
          0x2100,
          0x1d30,
          0x18f0,
          0x1470,
          0x0dd0,
          0x08d0,
          0x0cc0,
          0x0af0,
          0x0af0,
        },
    },

  [BATTERY_VARIANT_R] =
    {
      .DesignCap = 0x060c,
      .dPacc = 0x0c80,
      .dQacc = 0x0306,
      .ICHGTerm = 0x0120,
      .learncfg = 0x4402,
      .misccfg = 0x3830,
      .QRTable00 = 0x0480,
      .QRTable10 = 0x0280,
      .QRTable20 = 0x0200,
      .QRTable30 = 0x0200,
      .RCOMP0 = 0x002c,
      .relaxcfg = 0x023c,
      .TempCo = 0x0928,
      .Vempty = 0x965a,
      .OCVTable =
        {
          0x9870,
          0xb770,
          0xb8c0,
          0xba20,
          0xbb20,
          0xbc60,
          0xbd20,
          0xbe00,
          0xbf20,
          0xbfe0,
          0xc1a0,
          0xc2d0,
          0xc510,
          0xc650,
          0xc800,
          0xcc60,
        },
      .XTable =
        {
          0x00d0,
          0x19c0,
          0x1630,
          0x1310,
          0x15d0,
          0x2580,
          0x2890,
          0x1eb0,
          0x1ab0,
          0x1310,
          0x0a30,
          0x0ab0,
          0x0fc0,
          0x1000,
          0x06c0,
          0x06c0,
        },
    },
};

max17262_modelgauge_t* battery_config_get(const battery_variant_t variant) {
  ASSERT(variant < BATTERY_VARIANT_MAX);

  // Return E variant as default
  if (variant == BATTERY_VARIANT_DEFAULT) {
    return &modelgauge[BATTERY_VARIANT_E];
  }

  return &modelgauge[variant];
}
