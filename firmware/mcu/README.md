# MCU -- chip support

The MCU library provides support for a particular chip. Only main.c and HAL libraries
should call into this library.

## Relationship with gecko-sdk

Currently, `mcu` makes use of `gecko-sdk` for EFR32 chips to implement some functionality.
We do not call `gecko-sdk` directly from HAL libraries so that the HAL is chip-independent.
Over time, we aim to reduce usage of `gecko-sdk` as needed.

## Relationship with the HAL

`mcu` provides support for a chip, whereas the HAL provides drivers for the rest of firmware.
The HAL uses `mcu` to implement drivers for chip peripherals, like GPIOs or USARTs. The HAL
also implements drivers to interface with other ICs on the board, like NFC.
