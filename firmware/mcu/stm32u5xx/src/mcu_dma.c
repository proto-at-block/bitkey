#include "mcu_dma.h"

#include "mcu_dma_impl.h"
#include "mcu_nvic.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_dma.h"

#include <stddef.h>
#include <string.h>

typedef struct {
  mcu_dma_callback_t callback;
  void* user_param;
  bool allocated;
  mcu_dma_mode_t mode;
  LL_DMA_LinkNodeTypeDef node;  // Embedded node, not pointer
  DMA_TypeDef* instance;
  uint32_t channel_mask;
} channel_state_t;

static channel_state_t channel_table[MCU_DMA_MAX_CHANNELS] = {0};
static bool initialized = false;

static mcu_err_t mcu_dma_channel_configure_circular(uint32_t channel,
                                                    const mcu_dma_config_t* config);
static mcu_err_t mcu_dma_channel_configure_basic(uint32_t channel, const mcu_dma_config_t* config);

mcu_err_t mcu_dma_init(const int8_t nvic_priority) {
  if (initialized) {
    return MCU_ERROR_ALREADY_INITIALISED;
  }

  // Enable DMA clocks
  LL_AHB1_GRP1_EnableClock(LL_AHB1_GRP1_PERIPH_GPDMA1);
  LL_AHB3_GRP1_EnableClock(LL_AHB3_GRP1_PERIPH_LPDMA1);

// Enable SRAM3 for DMA operations (if available)
#ifdef LL_AHB1_GRP1_PERIPH_SRAM3
  LL_AHB1_GRP1_EnableClock(LL_AHB1_GRP1_PERIPH_SRAM3);
#endif

  // Clear channel states
  memset(channel_table, 0, sizeof(channel_table));

  // Configure NVIC priority for DMA interrupts (but don't enable yet)
  for (uint32_t i = 0; i < MCU_DMA_MAX_CHANNELS; i++) {
    mcu_nvic_set_priority(dma_irq_map[i].irqn, nvic_priority);
  }

  initialized = true;
  return MCU_ERROR_OK;
}

uint32_t mcu_dma_get_max_xfer_size(void) {
  return MCU_DMA_MAX_XFER_COUNT;
}

mcu_err_t mcu_dma_allocate_channel(uint32_t* channel) {
  if (!initialized) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  if (channel == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  // Find a free channel
  for (uint32_t i = 0; i < MCU_DMA_MAX_CHANNELS; i++) {
    if (!channel_table[i].allocated) {
      channel_table[i].allocated = true;
      channel_table[i].callback = NULL;
      channel_table[i].user_param = NULL;
      channel_table[i].instance = GPDMA1;
      channel_table[i].channel_mask = dma_irq_map[i].channel_mask;
      *channel = i;
      return MCU_ERROR_OK;
    }
  }

  return MCU_ERROR_DMA_CHANNELS_EXHAUSTED;
}

mcu_err_t mcu_dma_channel_free(uint32_t channel) {
  if (!initialized) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  if (channel >= MCU_DMA_MAX_CHANNELS) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  // Disable channel and interrupts
  LL_DMA_DisableChannel(GPDMA1, dma_irq_map[channel].channel_mask);
  LL_DMA_DisableIT_HT(GPDMA1, dma_irq_map[channel].channel_mask);
  LL_DMA_DisableIT_TC(GPDMA1, dma_irq_map[channel].channel_mask);
  LL_DMA_DisableIT_DTE(GPDMA1, dma_irq_map[channel].channel_mask);
  LL_DMA_DisableIT_ULE(GPDMA1, dma_irq_map[channel].channel_mask);
  LL_DMA_DisableIT_USE(GPDMA1, dma_irq_map[channel].channel_mask);

  // Clear channel state
  channel_table[channel].allocated = false;
  channel_table[channel].callback = NULL;
  channel_table[channel].user_param = NULL;

  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_channel_configure(uint32_t channel, const mcu_dma_config_t* config) {
  if (!initialized) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  if (channel >= MCU_DMA_MAX_CHANNELS || config == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  switch (config->mode) {
    case MCU_DMA_MODE_CIRCULAR:
      return mcu_dma_channel_configure_circular(channel, config);
    case MCU_DMA_MODE_BASIC:
      return mcu_dma_channel_configure_basic(channel, config);
    default:
      return MCU_ERROR_PARAMETER;
  }
}

mcu_err_t mcu_dma_channel_start(uint32_t channel) {
  if (!initialized || channel >= MCU_DMA_MAX_CHANNELS) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  channel_state_t* state = &channel_table[channel];
  LL_DMA_EnableChannel(state->instance, state->channel_mask);
  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_channel_stop(uint32_t channel) {
  if (!initialized || channel >= MCU_DMA_MAX_CHANNELS) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  channel_state_t* state = &channel_table[channel];
  LL_DMA_DisableChannel(state->instance, state->channel_mask);
  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_channel_is_active(uint32_t channel, bool* active) {
  if (!initialized || channel >= MCU_DMA_MAX_CHANNELS || active == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  channel_state_t* state = &channel_table[channel];
  *active = LL_DMA_IsEnabledChannel(state->instance, state->channel_mask);
  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_channel_get_remaining(uint32_t channel, uint32_t* remaining) {
  if (!initialized || channel >= MCU_DMA_MAX_CHANNELS || remaining == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  channel_state_t* state = &channel_table[channel];
  *remaining = LL_DMA_GetBlkDataLength(state->instance, state->channel_mask);

  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_channel_get_src_address(uint32_t channel, uintptr_t* addr) {
  if (!initialized || (channel >= MCU_DMA_MAX_CHANNELS) || (addr == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  channel_state_t* state = &channel_table[channel];
  *addr = LL_DMA_GetSrcAddress(state->instance, state->channel_mask);

  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_channel_get_dest_address(uint32_t channel, uintptr_t* addr) {
  if (!initialized || (channel >= MCU_DMA_MAX_CHANNELS) || (addr == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  channel_state_t* state = &channel_table[channel];
  *addr = LL_DMA_GetDestAddress(state->instance, state->channel_mask);

  return MCU_ERROR_OK;
}

mcu_err_t mcu_dma_channel_update_addresses(uint32_t channel, void* src_addr, void* dst_addr,
                                           uint32_t length) {
  if (!initialized || channel >= MCU_DMA_MAX_CHANNELS) {
    return MCU_ERROR_PARAMETER;
  }

  if (!channel_table[channel].allocated) {
    return MCU_ERROR_NOT_INITIALISED;
  }

  channel_state_t* state = &channel_table[channel];

  // Update node registers
  // LinkRegisters[2] is CSAR (source address)
  // LinkRegisters[3] is CDAR (destination address)
  // LinkRegisters[1] is CBR1 (block data length)
  if (src_addr != NULL) {
    state->node.LinkRegisters[2] = (uint32_t)src_addr;
  }
  if (dst_addr != NULL) {
    state->node.LinkRegisters[3] = (uint32_t)dst_addr;
  }
  if (length > 0) {
    state->node.LinkRegisters[1] = length;
  }

  // Update DMA configuration
  LL_DMA_ConfigLinkUpdate(
    state->instance, state->channel_mask,
    (LL_DMA_UPDATE_CTR1 | LL_DMA_UPDATE_CTR2 | LL_DMA_UPDATE_CBR1 | LL_DMA_UPDATE_CSAR |
     LL_DMA_UPDATE_CDAR | LL_DMA_UPDATE_CTR3 | LL_DMA_UPDATE_CBR2),
    (uint32_t)&state->node);

  return MCU_ERROR_OK;
}

// Common DMA interrupt handler
static void dma_irq_handler(uint32_t channel) {
  uint32_t channel_mask = dma_irq_map[channel].channel_mask;
  uint32_t flags = 0;

  // Check and clear interrupt flags
  if (LL_DMA_IsActiveFlag_HT(GPDMA1, channel_mask)) {
    LL_DMA_ClearFlag_HT(GPDMA1, channel_mask);
    flags |= MCU_DMA_FLAG_HALF_TRANSFER;
  }

  if (LL_DMA_IsActiveFlag_TC(GPDMA1, channel_mask)) {
    LL_DMA_ClearFlag_TC(GPDMA1, channel_mask);
    flags |= MCU_DMA_FLAG_TRANSFER_COMPLETE;
  }

  if (LL_DMA_IsActiveFlag_DTE(GPDMA1, channel_mask)) {
    LL_DMA_ClearFlag_DTE(GPDMA1, channel_mask);
    flags |= MCU_DMA_FLAG_TRANSFER_ERROR;
  }

  // Invoke callback
  if (channel_table[channel].callback) {
    (void)channel_table[channel].callback(channel, flags, channel_table[channel].user_param);
  }
}

// DMA interrupt handlers
void GPDMA1_Channel0_IRQHandler(void) {
  dma_irq_handler(0);
}
void GPDMA1_Channel1_IRQHandler(void) {
  dma_irq_handler(1);
}
void GPDMA1_Channel2_IRQHandler(void) {
  dma_irq_handler(2);
}
void GPDMA1_Channel3_IRQHandler(void) {
  dma_irq_handler(3);
}
void GPDMA1_Channel4_IRQHandler(void) {
  dma_irq_handler(4);
}
void GPDMA1_Channel5_IRQHandler(void) {
  dma_irq_handler(5);
}
void GPDMA1_Channel6_IRQHandler(void) {
  dma_irq_handler(6);
}
void GPDMA1_Channel7_IRQHandler(void) {
  dma_irq_handler(7);
}
void GPDMA1_Channel8_IRQHandler(void) {
  dma_irq_handler(8);
}
void GPDMA1_Channel9_IRQHandler(void) {
  dma_irq_handler(9);
}
void GPDMA1_Channel10_IRQHandler(void) {
  dma_irq_handler(10);
}
void GPDMA1_Channel11_IRQHandler(void) {
  dma_irq_handler(11);
}
void GPDMA1_Channel12_IRQHandler(void) {
  dma_irq_handler(12);
}
void GPDMA1_Channel13_IRQHandler(void) {
  dma_irq_handler(13);
}
void GPDMA1_Channel14_IRQHandler(void) {
  dma_irq_handler(14);
}
void GPDMA1_Channel15_IRQHandler(void) {
  dma_irq_handler(15);
}

static mcu_err_t mcu_dma_channel_configure_basic(uint32_t channel, const mcu_dma_config_t* config) {
  // No input validation, since caller has already performed validation.
  channel_state_t* state = &channel_table[channel];

  // Configure DMA channel for normal mode
  LL_DMA_InitTypeDef DMA_InitStruct = {0};

  // Direction
  if (config->direction == MCU_DMA_DIR_M2P) {
    DMA_InitStruct.Direction = LL_DMA_DIRECTION_MEMORY_TO_PERIPH;
  } else if (config->direction == MCU_DMA_DIR_P2M) {
    DMA_InitStruct.Direction = LL_DMA_DIRECTION_PERIPH_TO_MEMORY;
  } else {
    DMA_InitStruct.Direction = LL_DMA_DIRECTION_MEMORY_TO_MEMORY;
  }

  // Request line
  DMA_InitStruct.Request = config->request;

  // Data width
  DMA_InitStruct.SrcDataWidth =
    (config->src_width == MCU_DMA_SIZE_1_BYTE)    ? LL_DMA_SRC_DATAWIDTH_BYTE
    : (config->src_width == MCU_DMA_SIZE_2_BYTES) ? LL_DMA_SRC_DATAWIDTH_HALFWORD
                                                  : LL_DMA_SRC_DATAWIDTH_WORD;
  DMA_InitStruct.DestDataWidth =
    (config->dst_width == MCU_DMA_SIZE_1_BYTE)    ? LL_DMA_DEST_DATAWIDTH_BYTE
    : (config->dst_width == MCU_DMA_SIZE_2_BYTES) ? LL_DMA_DEST_DATAWIDTH_HALFWORD
                                                  : LL_DMA_DEST_DATAWIDTH_WORD;

  // Increment mode
  DMA_InitStruct.SrcIncMode = config->src_increment ? LL_DMA_SRC_INCREMENT : LL_DMA_SRC_FIXED;
  DMA_InitStruct.DestIncMode = config->dst_increment ? LL_DMA_DEST_INCREMENT : LL_DMA_DEST_FIXED;

  // Transfer settings
  DMA_InitStruct.BlkHWRequest = LL_DMA_HWREQUEST_SINGLEBURST;
  DMA_InitStruct.DataAlignment = LL_DMA_DATA_ALIGN_ZEROPADD;
  DMA_InitStruct.SrcBurstLength = 1;
  DMA_InitStruct.DestBurstLength = 1;
  DMA_InitStruct.Priority = config->priority;
  DMA_InitStruct.TriggerMode = LL_DMA_TRIGM_BLK_TRANSFER;
  DMA_InitStruct.TriggerPolarity = LL_DMA_TRIG_POLARITY_MASKED;
  DMA_InitStruct.TriggerSelection = 0x00000000U;
  DMA_InitStruct.TransferEventMode = LL_DMA_TCEM_BLK_TRANSFER;
  DMA_InitStruct.SrcAllocatedPort = LL_DMA_SRC_ALLOCATED_PORT1;
  DMA_InitStruct.DestAllocatedPort = LL_DMA_DEST_ALLOCATED_PORT0;
  DMA_InitStruct.LinkAllocatedPort = LL_DMA_LINK_ALLOCATED_PORT1;
  DMA_InitStruct.LinkStepMode = LL_DMA_LSM_FULL_EXECUTION;
  DMA_InitStruct.LinkedListBaseAddr = 0x00000000U;
  DMA_InitStruct.LinkedListAddrOffset = 0x00000000U;
  // Addresses and length
  DMA_InitStruct.SrcAddress = (uint32_t)config->src_addr;
  DMA_InitStruct.DestAddress = (uint32_t)config->dst_addr;
  DMA_InitStruct.BlkDataLength = config->length;

  // Store callback and mode
  state->callback = config->callback;
  state->user_param = config->user_param;
  state->mode = config->mode;

  // Initialize DMA
  LL_DMA_Init(state->instance, state->channel_mask, &DMA_InitStruct);

  // Enable interrupts
  LL_DMA_EnableIT_TC(state->instance, state->channel_mask);
  LL_DMA_EnableIT_HT(state->instance, state->channel_mask);
  LL_DMA_EnableIT_DTE(state->instance, state->channel_mask);

  // Enable NVIC for this channel
  mcu_nvic_enable_irq(dma_irq_map[channel].irqn);

  return MCU_ERROR_OK;
}

static mcu_err_t mcu_dma_channel_configure_circular(uint32_t channel,
                                                    const mcu_dma_config_t* config) {
  // No pointer validation, since caller has already performed validation.
  if (config->xfer_node == NULL) {
    // Linked List node must be provided for circular transfers
    return MCU_ERROR_PARAMETER;
  }

  channel_state_t* state = &channel_table[channel];

  LL_DMA_InitNodeTypeDef DMA_InitNode = {0};

  // Direction
  if (config->direction == MCU_DMA_DIR_M2P) {
    DMA_InitNode.Direction = LL_DMA_DIRECTION_MEMORY_TO_PERIPH;
  } else if (config->direction == MCU_DMA_DIR_P2M) {
    DMA_InitNode.Direction = LL_DMA_DIRECTION_PERIPH_TO_MEMORY;
  } else {
    DMA_InitNode.Direction = LL_DMA_DIRECTION_MEMORY_TO_MEMORY;
  }

  // Request line
  DMA_InitNode.Request = config->request;

  // Data width
  DMA_InitNode.SrcDataWidth = (config->src_width == MCU_DMA_SIZE_1_BYTE) ? LL_DMA_SRC_DATAWIDTH_BYTE
                              : (config->src_width == MCU_DMA_SIZE_2_BYTES)
                                ? LL_DMA_SRC_DATAWIDTH_HALFWORD
                                : LL_DMA_SRC_DATAWIDTH_WORD;
  DMA_InitNode.DestDataWidth =
    (config->dst_width == MCU_DMA_SIZE_1_BYTE)    ? LL_DMA_DEST_DATAWIDTH_BYTE
    : (config->dst_width == MCU_DMA_SIZE_2_BYTES) ? LL_DMA_DEST_DATAWIDTH_HALFWORD
                                                  : LL_DMA_DEST_DATAWIDTH_WORD;

  // Increment mode
  DMA_InitNode.SrcIncMode = config->src_increment ? LL_DMA_SRC_INCREMENT : LL_DMA_SRC_FIXED;
  DMA_InitNode.DestIncMode = config->dst_increment ? LL_DMA_DEST_INCREMENT : LL_DMA_DEST_FIXED;

  // Transfer settings
  DMA_InitNode.BlkHWRequest = LL_DMA_HWREQUEST_SINGLEBURST;
  DMA_InitNode.DataAlignment = LL_DMA_DATA_ALIGN_ZEROPADD;
  DMA_InitNode.SrcBurstLength = 1;
  DMA_InitNode.DestBurstLength = 1;
  DMA_InitNode.TriggerMode = LL_DMA_TRIGM_BLK_TRANSFER;
  DMA_InitNode.TriggerPolarity = LL_DMA_TRIG_POLARITY_MASKED;
  DMA_InitNode.TriggerSelection = 0x00000000U;
  DMA_InitNode.TransferEventMode = LL_DMA_TCEM_BLK_TRANSFER;
  DMA_InitNode.SrcAllocatedPort = LL_DMA_SRC_ALLOCATED_PORT1;
  DMA_InitNode.DestAllocatedPort = LL_DMA_DEST_ALLOCATED_PORT0;

  // Addresses and length
  DMA_InitNode.SrcAddress = (uint32_t)config->src_addr;
  DMA_InitNode.DestAddress = (uint32_t)config->dst_addr;
  DMA_InitNode.BlkDataLength = config->length;

  // Refresh registers when moving to next linked node:
  //   1. CTR1: Specifies ports and burst length
  //   2. CTR2: Specifies transfer mode, request signal and direction
  //   3. CBR1: Specifies block length
  //   4. CSAR: Specifies source address of transfer
  //   5. CDAR: Specifies destination address of transfer
  //   6. CLLR: Specifies offset of linked node
  const uint32_t update_regs = (LL_DMA_UPDATE_CTR1 | LL_DMA_UPDATE_CTR2 | LL_DMA_UPDATE_CBR1 |
                                LL_DMA_UPDATE_CSAR | LL_DMA_UPDATE_CDAR | LL_DMA_UPDATE_CLLR);
  DMA_InitNode.UpdateRegisters = update_regs;

  // Linear addressing mode for GPDMA (1D transfers)
  DMA_InitNode.NodeType = LL_DMA_GPDMA_LINEAR_NODE;

  // Bind node to the state instance
  LL_DMA_CreateLinkNode(&DMA_InitNode, config->xfer_node);

  // Connect the node to itself to generate a linked list transfer
  // Note: The offset specified here is a bit of a weird quirk of the LL which
  // requires you to know how the LL code structures the registers in memory;
  // the mapping is given below for a 1D transfer:
  //   0: CTR1
  //   1: CTR2
  //   2: CBR1
  //   3: CSAR
  //   4: CDAR
  //   5: CLLR
  // If any of the registers before the CLLR are not specified in the
  // 'UpdateRegisters', then the offset must be adjusted to account for that
  // (e.g. if you remove CTR1 from 'UpdateRegisters', then the offset must be
  // changed to 'LL_DMA_CLLR_OFFSET4')
  LL_DMA_ConnectLinkNode(config->xfer_node, LL_DMA_CLLR_OFFSET5, config->xfer_node,
                         LL_DMA_CLLR_OFFSET5);

  // Write the address of the linked list node to the DMA channel; it will be
  // next node loaded
  LL_DMA_SetLinkedListBaseAddr(state->instance, state->channel_mask,
                               (uint32_t)(uintptr_t)config->xfer_node);
  LL_DMA_ConfigLinkUpdate(state->instance, state->channel_mask, update_regs,
                          (uint32_t)(uintptr_t)config->xfer_node);

  // Store callback and mode
  state->callback = config->callback;
  state->user_param = config->user_param;
  state->mode = config->mode;

  // Initialize Linked List DMA
  LL_DMA_InitLinkedListTypeDef DMA_InitLinkedList = {0};
  DMA_InitLinkedList.Priority = config->priority;
  DMA_InitLinkedList.LinkAllocatedPort = LL_DMA_LINK_ALLOCATED_PORT1;

  // Generate Half-Transfer and Full-Transfer IRQs at the end of each linked item
  DMA_InitLinkedList.TransferEventMode = LL_DMA_TCEM_EACH_LLITEM_TRANSFER;

  // Execute the full linked list (continue indefinitely)
  DMA_InitLinkedList.LinkStepMode = LL_DMA_LSM_FULL_EXECUTION;

  // Finally initialize the DMA
  LL_DMA_List_Init(state->instance, state->channel_mask, &DMA_InitLinkedList);

  // Enable interrupts
  LL_DMA_EnableIT_TC(state->instance, state->channel_mask);
  LL_DMA_EnableIT_HT(state->instance, state->channel_mask);
  LL_DMA_EnableIT_DTE(state->instance, state->channel_mask);

  // Enable NVIC for this channel
  mcu_nvic_enable_irq(dma_irq_map[channel].irqn);

  return MCU_ERROR_OK;
}
