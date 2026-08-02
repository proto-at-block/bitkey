# SystemView for Wallet Firmware

SEGGER SystemView SDK and Wallet-tailored integration for tracing the W3
firmware over J-Link RTT.

## Layout

- `SEGGER/`            — SystemView + RTT core sources (compiled into firmware
                         when `--sysview` is enabled).
- `Config/`            — `SEGGER_SYSVIEW_Conf.h`, `SEGGER_RTT_Conf.h`, `Global.h`.
- `freertos/`          — FreeRTOS port (`SEGGER_SYSVIEW_FreeRTOS.[ch]`).
                         Compiled into the FreeRTOS library when sysview is on.
- `inc/`               — Public headers (`rtos_sysview.h`, `rtos_sysview_trace.h`).
- `src/`               — Shared Wallet source (`rtos_sysview.c`).
- `src/stm32/`         — STM32-specific integration (interrupt list, trace shim).
- `src/efr32/`         — EFR32-specific integration (future).
- `include/`           — Generated header stubs (`SYSVIEW_Peripherals.h`).
- `Description/`       — Wallet-specific `SYSVIEW_Peripherals.txt` module
                         description used for header generation and CSV export.
- `scripts/`           — Wallet helper scripts:
    - `capture_trace.sh` — Headless RTT capture and optional CSV export.
    - `description_to_h.py` — Convert a SEGGER description file into a C header.
- `w3-uxc/`            — Host SystemView project file pre-configured for the
                         W3 UXC (STM32U585ZI, SWD, RTT auto-detect).

## Build

SystemView is gated by a meson option that defaults to **off**:

```bash
inv build -p w3-uxc --target w3a-uxc-pdvt-app-a-dev --sysview
```

When the option is enabled the build:
- Defines `USE_SYSVIEW=1` (and `USE_RTT=1`) project-wide.
- Compiles the SEGGER core, the FreeRTOS port, and `src/rtos_sysview.c`
  into the FreeRTOS library.
- Generates `SYSVIEW_Peripherals.h` from `Description/SYSVIEW_Peripherals.txt`
  and injects it into the STM32U5 build so peripheral trace IDs stay in sync
  with the host-side description file.
- Activates the FreeRTOS trace hooks in `firmware/config/rtos/*/FreeRTOSConfig.h`
  (`traceTASK_CREATE`/`DELETE`/`SWITCH_IN`/`OUT`, ISR enter/exit, etc.).
- Calls `SEGGER_SYSVIEW_Conf()` from each platform's `main()` before the
  scheduler is started.

The non-sysview aliases and the default meson configuration are unchanged.

## Capturing a Trace

1. Build and flash with sysview enabled (`_w3_build_flash_uxc_sysview`).
2. Run `firmware/lib/sysview/scripts/capture_trace.sh --platform w3-uxc`.
3. The script opens an RTT logger, explicitly writes the SystemView `START`
   command on the RTT down-channel for the selected SystemView channel, and
   writes the trace to `.SVDat`.
4. Press `Ctrl-C` when finished. If `--csv` was provided, the script converts
   the trace to CSV using repo-local tooling and the description files in
   `Description/` (or an override passed via `--description`). On stop
   the script also writes the SystemView `STOP` command over RTT.

The default RTT control block search uses J-Link auto-detection; no manual
address is required.
