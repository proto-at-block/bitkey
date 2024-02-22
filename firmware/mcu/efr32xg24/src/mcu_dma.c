#include "mcu_dma.h"

#include "em_core.h"
#include "em_ldma.h"

#include <stddef.h>

typedef enum { MCU_DMA_MODE_BASIC, MCU_DMA_MODE_PING_PONG } mcu_dma_mode_t;
typedef enum { MCU_DMA_DIR_M2P, MCU_DMA_DIR_P2M } mcu_dma_direction_t;

typedef struct {
  mcu_dma_callback_t callback;
  void* user_param;
  uint32_t callback_count;
  bool allocated;
  mcu_dma_mode_t mode;
} channel_table_t;

typedef struct {
  LDMA_Descriptor_t desc[2];
} dma_transfer_t;

static bool initialised = false;
static channel_table_t channels[LDMA_CH_NUM];

static dma_transfer_t dma_transfer[LDMA_CH_NUM];

static mcu_err_t start_transfer(mcu_dma_mode_t mode, mcu_dma_direction_t direction,
                                uint32_t channel, mcu_dma_signal_t signal, void* buf0, void* buf1,
                                void* buf2, bool buf_inc, int len, mcu_dma_data_size_t size,
                                mcu_dma_callback_t callback, void* user_param);

mcu_err_t mcu_dma_init(const int8_t nvic_priority) {
  CORE_DECLARE_IRQ_STATE;

  LDMA_Init_t dma_init = LDMA_INIT_DEFAULT;
  dma_init.ldmaInitCtrlNumFixed = 0;

  CORE_ENTER_ATOMIC();
  if (initialised) {
    CORE_EXIT_ATOMIC();
    return MCU_ERROR_ALREADY_INITIALISED;
  }

  initialised = true;
  CORE_EXIT_ATOMIC();

  if (nvic_priority >= (1 << __NVIC_PRIO_BITS)) {
    return MCU_ERROR_PARAMETER;
  }

  for (uint32_t i = 0; i < (uint32_t)LDMA_CH_NUM; i++) {
    channels[i].allocated = false;
  }

  dma_init.ldmaInitIrqPriority = nvic_priority;
  LDMA_Init(&dma_init);

  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_allocate_channel(uint32_t* channel, mcu_dma_callback_t callback) {
  CORE_DECLARE_IRQ_STATE;

  if (!initialised) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  if (channel == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  CORE_ENTER_ATOMIC();
  for (uint32_t i = 0; i < LDMA_CH_NUM; i++) {
    if (!channels[i].allocated) {
      *channel = i;
      channels[i].allocated = true;
      channels[i].callback = callback;
      CORE_EXIT_ATOMIC();
      return MCU_ERROR_OK;
    }
  }
  CORE_EXIT_ATOMIC();

  return MCU_ERROR_DMA_CHANNELS_EXHAUSTED;
}

mcu_err_t mcu_dma_peripheral_memory(uint32_t channel, mcu_dma_signal_t signal, void* dst, void* src,
                                    bool dst_inc, int len, mcu_dma_data_size_t size,
                                    mcu_dma_callback_t callback, void* user_param) {
  return start_transfer(MCU_DMA_MODE_BASIC, MCU_DMA_DIR_P2M, channel, signal, dst, src, NULL,
                        dst_inc, len, size, callback, user_param);
}

mcu_err_t mcu_dma_memory_peripheral(int32_t channel, mcu_dma_signal_t signal, void* dst, void* src,
                                    bool src_inc, int len, mcu_dma_data_size_t size,
                                    mcu_dma_callback_t callback, void* user_param) {
  return start_transfer(MCU_DMA_MODE_BASIC, MCU_DMA_DIR_M2P, channel, signal, dst, src, NULL,
                        src_inc, len, size, callback, user_param);
}

static mcu_err_t start_transfer(mcu_dma_mode_t mode, mcu_dma_direction_t direction,
                                uint32_t channel, mcu_dma_signal_t signal, void* buf0, void* buf1,
                                void* buf2, bool buf_inc, int len, mcu_dma_data_size_t size,
                                mcu_dma_callback_t callback, void* user_param) {
  channel_table_t* ch;
  LDMA_TransferCfg_t xfer;
  LDMA_Descriptor_t* desc;

  if (!initialised) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  if ((channel >= LDMA_CH_NUM) || (buf0 == NULL) || (buf1 == NULL) ||
      (len > MCU_DMA_MAX_XFER_COUNT) || ((mode == MCU_DMA_MODE_PING_PONG) && (buf2 == NULL))) {
    return MCU_ERROR_PARAMETER;
  }

  ch = &channels[channel];
  if (ch->allocated == false) {
    return MCU_ERROR_DMA_CHANNEL_NOT_ALLOC;
  }

  xfer = (LDMA_TransferCfg_t)LDMA_TRANSFER_CFG_PERIPHERAL(0);
  desc = &dma_transfer[channel].desc[0];

  if (direction == MCU_DMA_DIR_M2P) {
    *desc = (LDMA_Descriptor_t)LDMA_DESCRIPTOR_SINGLE_M2P_BYTE(NULL, NULL, 1UL);
    if (!buf_inc) {
      desc->xfer.srcInc = ldmaCtrlSrcIncNone;
    }
  } else {
    *desc = (LDMA_Descriptor_t)LDMA_DESCRIPTOR_SINGLE_P2M_BYTE(NULL, NULL, 1UL);
    if (!buf_inc) {
      desc->xfer.dstInc = ldmaCtrlDstIncNone;
    }
  }

  xfer.ldmaReqSel = signal;
  desc->xfer.xferCnt = len - 1;
  desc->xfer.dstAddr = (uint32_t)(uint8_t*)buf0;
  desc->xfer.srcAddr = (uint32_t)(uint8_t*)buf1;
  desc->xfer.size = size;

  if (mode == MCU_DMA_MODE_PING_PONG) {
    desc->xfer.linkMode = ldmaLinkModeRel;
    desc->xfer.link = 1;
    desc->xfer.linkAddr = 4; /* Refer to the "pong" descriptor. */

    /* Set the "pong" descriptor equal to the "ping" descriptor. */
    dma_transfer[channel].desc[1] = *desc;
    /* Refer to the "ping" descriptor. */
    dma_transfer[channel].desc[1].xfer.linkAddr = -4;
    dma_transfer[channel].desc[1].xfer.srcAddr = (uint32_t)(uint8_t*)buf2;

    if (direction == MCU_DMA_DIR_P2M) {
      dma_transfer[channel].desc[1].xfer.dstAddr = (uint32_t)(uint8_t*)buf1;
      desc->xfer.srcAddr = (uint32_t)(uint8_t*)buf2;
    }
  }

  /* Whether an interrupt is needed. */
  if ((callback == NULL) && (mode == MCU_DMA_MODE_BASIC)) {
    desc->xfer.doneIfs = 0;
  }

  ch->callback = callback;
  ch->user_param = user_param;
  ch->callback_count = 0;
  ch->mode = mode;

  LDMA_StartTransfer(channel, &xfer, desc);

  return MCU_ERROR_OK;
}

void LDMA_IRQHandler(void) {
  bool stop;
  channel_table_t* ch;
  uint32_t pending, chnum, chmask;

  /* Get all pending and enabled interrupts. */
  pending = LDMA->IF;
  pending &= LDMA->IEN;

  /* Check for LDMA error. */
  if (pending & LDMA_IF_ERROR) {
    /* Loop to enable debugger to see what has happened. */
    while (true) {
      /* Wait forever. */
      // TODO: replace this with proper error handling
    }
  }

  /* Iterate over all LDMA channels. */
  for (chnum = 0, chmask = 1; chnum < LDMA_CH_NUM; chnum++, chmask <<= 1) {
    if (pending & chmask) {
      /* Clear the interrupt flag. */
#if defined(LDMA_HAS_SET_CLEAR)
      LDMA->IF_CLR = chmask;
#else
      LDMA->IFC = chmask;
#endif

      ch = &channels[chnum];
      if (ch->callback != NULL) {
        ch->callback_count++;
        stop = !ch->callback(chnum, ch->callback_count, ch->user_param);

        if ((ch->mode == MCU_DMA_MODE_PING_PONG) && stop) {
          dma_transfer[chnum].desc[0].xfer.link = 0;
          dma_transfer[chnum].desc[1].xfer.link = 0;
        }
      }
    }
  }
}
