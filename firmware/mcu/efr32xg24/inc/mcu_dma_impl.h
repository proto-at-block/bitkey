
#include "em_ldma.h"

// Maximum length of one DMA transfer for EFR32
#define MCU_DMA_MAX_XFER_COUNT \
  ((int)((_LDMA_CH_CTRL_XFERCNT_MASK >> _LDMA_CH_CTRL_XFERCNT_SHIFT) + 1))

// EFR32 DMA signal definitions
enum mcu_dma_signal_t {
  MCU_DMA_SIGNAL_NONE = LDMAXBAR_CH_REQSEL_SOURCESEL_NONE,
  MCU_DMA_SIGNAL_TIMER0_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER0_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER0_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER0_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER1_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_TIMER1_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_TIMER1_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_TIMER1_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_USART0_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0RXDATAV | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_RXDATAVRIGHT =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0RXDATAVRIGHT | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_TXBL =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0TXBL | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_TXBLRIGHT =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0TXBLRIGHT | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_TXEMPTY =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0TXEMPTY | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_I2C0_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_I2C0RXDATAV | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C0,
  MCU_DMA_SIGNAL_I2C0_TXBL = LDMAXBAR_CH_REQSEL_SIGSEL_I2C0TXBL | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C0,
  MCU_DMA_SIGNAL_I2C1_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_I2C1RXDATAV | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C1,
  MCU_DMA_SIGNAL_I2C1_TXBL = LDMAXBAR_CH_REQSEL_SIGSEL_I2C1TXBL | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C1,
  MCU_DMA_SIGNAL_AGC_RSSI = LDMAXBAR_CH_REQSEL_SIGSEL_AGCRSSI | LDMAXBAR_CH_REQSEL_SOURCESEL_AGC,
  MCU_DMA_SIGNAL_PROTIMER_BOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERBOF | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC3 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC3 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC4 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC4 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_POF =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERPOF | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_WOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERWOF | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_MODEM_DEBUG =
    LDMAXBAR_CH_REQSEL_SIGSEL_MODEMDEBUG | LDMAXBAR_CH_REQSEL_SOURCESEL_MODEM,
  MCU_DMA_SIGNAL_IADC0_IADC_SCAN =
    LDMAXBAR_CH_REQSEL_SIGSEL_IADC0IADC_SCAN | LDMAXBAR_CH_REQSEL_SOURCESEL_IADC0,
  MCU_DMA_SIGNAL_IADC0_IADC_SINGLE =
    LDMAXBAR_CH_REQSEL_SIGSEL_IADC0IADC_SINGLE | LDMAXBAR_CH_REQSEL_SOURCESEL_IADC0,
  MCU_DMA_SIGNAL_TIMER2_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER2_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER2_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER2_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER3_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_TIMER3_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_TIMER3_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_TIMER3_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_EUSART0_TXBL =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART0TXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART0,
  MCU_DMA_SIGNAL_EUSART0_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART0RXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART0,
  MCU_DMA_SIGNAL_EUSART1_TXBL =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART1TXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART1,
  MCU_DMA_SIGNAL_EUSART1_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART1RXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART1,
};

/**
 * @brief Binds a callback and callback pointer for the allocated DMA channel
 * identified by @p channel.
 *
 * @param channel     Identifier for the allocated DMA channel.
 * @param user_param  Pointer to pass to the callback on invocation.
 *
 * @return `MCU_ERR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 */
mcu_err_t mcu_dma_channel_configure(uint32_t channel, void* user_param);

/**
 * @brief Starts a ping-pong LDMA transfer from a peripheral to memory.
 *
 * @details Performs a ping-pong LDMA transfer, jumping between @p dst1 and
 * @p dst2 after @p len bytes have been written to the active buffer.
 *
 * @param[in]  channel     Identifier for the allocated DMA channel.
 * @param[in]  signal      LDMA signal
 * @param[out] dst1        Destination to write bytes to initially and after @p dst2.
 * @param[out] dst2        Destination to write bytes to after @p dst1.
 * @param[in]  src         Source to read bytes from.
 * @param[in]  dst_inc     `true` if the destination should be incremented on each DMA transfer.
 * @param[in]  len         Length of the @p dst1 and @p dst2 buffers in bytes.
 * @param[in]  size        Number of bytes to transfer before invoking the callback.
 * @param[in]  callback    Callback to invoke on DMA transfer complete.
 * @param[in]  user_param  User parameter to pass to the specified @p callback.
 *
 * @return `MCU_ERR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 */
mcu_err_t mcu_dma_peripheral_memory_ping_pong(int32_t channel, mcu_dma_signal_t signal, void* dst1,
                                              void* dst2, void* src, bool dst_inc, int len,
                                              mcu_dma_data_size_t size, mcu_dma_callback_t callback,
                                              void* user_param);

/**
 * @brief Starts a ping-pong LDMA transfer from memory to a peripheral.
 *
 * @details Performs a ping-pong LDMA transfer, jumping between @p src1 and
 * @p src2 after @p len bytes have been written to the @p dst.
 *
 * @param[in]  channel     Identifier for the allocated DMA channel.
 * @param[in]  signal      LDMA signal
 * @param[out] dst         Destination to write bytes to.
 * @param[out] src1        Initial buffer to read bytes from and after reading from @p src2.
 * @param[in]  src2        Buffer to read bytes from after @p src1.
 * @param[in]  src_inc     `true` if the source should be incremented on each DMA transfer.
 * @param[in]  len         Length of the @p src1 and @p src2 buffers in bytes.
 * @param[in]  size        Number of bytes to transfer before invoking the callback.
 * @param[in]  callback    Callback to invoke on DMA transfer complete.
 * @param[in]  user_param  User parameter to pass to the specified @p callback.
 *
 * @return `MCU_ERR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 */
mcu_err_t mcu_dma_memory_peripheral_ping_pong(int32_t channel, mcu_dma_signal_t signal, void* dst,
                                              void* src1, void* src2, bool src_inc, int len,
                                              mcu_dma_data_size_t size, mcu_dma_callback_t callback,
                                              void* user_param);
