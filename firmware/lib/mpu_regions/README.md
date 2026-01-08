# MPU Task Isolation

We achieve task isolation through the use of the [FreeRTOS MPU implementation](#freertos-mpu).

## Table of Contents

1. [Resources](#resources)
2. [FreeRTOS MPU](#freertos-mpu)
4. [SMU](#smu)
5. [Our Implementation](#our-implementation)
    - [Task Table](#task-table)
    - [Loader and Attributes](#loader-and-attributes)
    - [Our FreeRTOS delta](#our-freertos-delta)
    - [Syscalls](#syscalls)
    - [FreeRTOS Config values](#freertos-config-values)
    - [Debugging Memory Faults](#debugging-memory-faults)
    - [Future Improvements](#future-improvements)

## Resources

Helpful resources are listed below:

- [FreeRTOS MPU landing page](https://www.freertos.org/FreeRTOS-MPU-memory-protection-unit.html)
- [FreeRTOS on ARMv8-M](https://www.freertos.org/2020/04/using-freertos-on-armv8-m-microcontrollers.html#FREERTOS_WITH_MPU)
- [FreeRTOS kernel threat model](https://www.freertos.org/security/kernel-threat-model.html)
- [FreeRTOS ebook](https://www.freertos.org/MPU_Chapter.pdf)(**warning**: outdated, written for armv7, some misleading information/errata)
- [ARMv8-M MPU](https://arm-software.github.io/CMSIS_5/Core/html/group__mpu8__functions.html)
- [Silicon Labs EFR32xG24 reference manual](https://www.silabs.com/documents/public/reference-manuals/efr32xg24-rm.pdf)
- [Silicon Labs SMU](https://docs.silabs.com/gecko-platform/4.1/emlib/api/efr32xg21/group-smu)
- [Silicon Labs Secure Key Storage](https://www.silabs.com/documents/public/application-notes/an1271-efr32-secure-key-storage.pdf)
- [Silicon Labs TrustZone](https://www.silabs.com/documents/public/application-notes/an1374-trustzone.pdf) (contains helpful SMU information)

## FreeRTOS MPU

The FreeRTOS MPU implementation can be used to isolate tasks from safeguarded resources and other tasks. Compromise of one task will only jeopardize resources that task has been granted access to.

Our SoC provides 16 configurable MPU regions. The FreeRTOS MPU implementation uses 5 of these regions to set the following base permissions:

| Region # | Permissions | Region Boundaries |
| ---- | ---- | ---- |
| 0 | privileged RX | **privileged_functions_start**<br />**privileged_functions_end** |
| 1 | global RX | **unprivileged_flash_start**<br />**unprivileged_flash_end** |
| 2 | global RX (unprivileged syscalls) | **syscalls_flash_start**<br />**syscalls_flash_end** |
| 3 | privileged RW (kernel data) | **privileged_sram_start**<br />**privileged_sram_end** |
| 4 | unprivileged task-only RW, privileged RW | task stack |

The setup of these regions can be found under [port.c](https://github.com/FreeRTOS/FreeRTOS-Kernel/blob/5a9d7c8388c32ff0bc530cd713f32e15c3e38d52/portable/GCC/ARM_CM33_NTZ/non_secure/port.c) where function *prvSetupMPU* contains the first 4 regions and function *vPortStoreTaskMPUSettings* has the task stack region and also configures the task-specific permissions. This leaves us with 11 regions for the task-specific permissions implementation. How these regions are used and how many regions each task needs can be found in [mpu_regions.c](src/mpu_regions.c).

It is also important to note that every MPU region must be 32-byte aligned in address and size, and also cannot overlap with any other existing region.

## SMU

SMU, or the Security Management Unit, is used to configure TrustZone bus level security and MPU peripheral accesses.
Peripheral access can be configured based on privileged and secure(TrustZone) attributes. Since we don't use TrustZone, we are only concerned with the privileged configuration.

The default configuration blocks all unprivileged accesses to peripherals. In order for an unprivileged task to talk to these peripherals, the peripheral needs to be explicitly configured for unprivileged access. This is in addition to the peripheral registers being marked **R** or **RW** in the task's MPU configuration.

See Section 10 of the [Silicon Labs Reference Manual](#resources) or the Silicon Labs TrustZone and SMU pages under [Resources](#resources) for more information. Our configuration can be found under [mcu_smu.c](../../mcu/efr32xg24/src/mcu_smu.c)

### Gotchas

It seems that allowing unprivileged tasks to talk with the SEMAILBOX peripheral does not grant total permissions to talk to SEMAILBOX. Calls to the SEMAILBOX that deal with keys stored in the Secure Vault High will fail (see Secure Key Storage under [Resources](#resources)). For the time being, any functions dealing with the Secure Vault will need to be [syscalls](#syscalls). There may be a workaround using the [SMU](#smu), but this will require further research.

## Our Implementation

Files of interest:

- [rtos_mpu.h](../rtos/inc/rtos_mpu.h)
- [mpu_regions.c](src/mpu_regions.c)
- [mcu_smu.c](../../mcu/efr32xg24/src/mcu_smu.c)
- [efr32mg24.jinja.ld](../../config/partitions/w1a/efr32mg24.jinja.ld)
- [attributes.h](../helpers/attributes.h)

At a high level, we aim to meet the following safeguards:

      NX stack: FreeRTOS region 4 marks each task's stack as RW
      RW inactive slot: implemented in mpu_regions.c
      RO bl: implemented in mpu_regions.c
      RX ramfuncs: implemented in mpu_regions.c
      RX active slot: FreeRTOS regions 0-3 configure the safeguards here

The following sub-sections will discuss task permissions at a high level.

### Task Table

The table here may become stale, so it is a good idea to double-check with [mpu_regions.c](src/mpu_regions.c)

| Name  | Privileged  | Peripheral Access Needed | Loader-specified regions |
| ---- | ---- | ---- | ---- |
| fwup | no | CMU, MSC, SEMAILBOX | shared task protected bss<br />shared and fwup task data(bss, data)<br /> filesystem (RO)<br />ramfuncs (RX)<br /> inactive slot (RW)<br />active slot metadata and properties (RO)<br /> bootloader (RO) |
| led | no | TIMER0, TIMER1, GPIO | shared task protected bss<br />shared task data<br /> led task data <br /> filesystem (RO)|
| nfc_isr | no | GPIO, I2C1 | shared task protected bss<br />shared task data<br /> nfc task (bss, data) <br /> filesystem (RO)|
| nfc_thread | no (starts off priv) | GPIO, I2C1, CMU | shared task protected bss<br />shared task(bss, data)<br /> nfc task(bss, data) <br /> filesystem (RO)|
| auth_matching | yes (TODO: unpriv) | shared task protected bss<br />privileged tasks can access all peripherals | shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| auth_main | yes (TODO: unpriv) | privileged tasks can access all peripherals| shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| key_manager | yes (TODO: unpriv) | privileged tasks can access all peripherals| shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| sysinfo | yes (TODO: unk) | privileged tasks can access all peripherals|shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| power | yes | privileged tasks can access all peripherals|shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| tamper | yes | privileged tasks can access all peripherals|shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| fs_mount | yes | privileged tasks can access all peripherals|shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| mfgtest | yes (only exists in dev) | privileged tasks can access all peripherals|shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|
| shell | yes (only exists in dev) | privileged tasks can access all peripherals| shared task protected bss<br />inactive slot (RW)<br /> bootloader <br /> ramfuncs (RX) <br /> active slot(metadata, properties, signature) (RO)|

Note: the fwup task hits our 11 region limit, so shared bss and data are combined with the fwup bss and fwup data. See [efr32mg24.jinja.ld](../../config/partitions/w1a/efr32mg24.jinja.ld).
Shared task protected bss stores the global stack canary value, which we want to ensure every task can read but none can modify.

### Loader and Attributes

There is a challenge associated with how to best give an unprivileged task access to a global variable.
Depending on the variable and how frequently it shows up we either:

- Move the variable to a particular spot in the linker using compiler attributes (see [attributes.h](../helpers/attributes.h)). Note that this can only move a variable into the `.data` section
- Move the object file's `.bss` or `.data` sections into a particular spot in the linker. (note that `.bss` can be moved into `.data`, but doing so increases fw size)

The first case is helpful when we only want to give access to a particular variable in a file and keep the other globals in that file separate.
The second case is needed when working with libraries where we only have an object file to work with, or if all of the variables in the file should be given the same access.

See [efr32mg24.jinja.ld](../../config/partitions/w1a/efr32mg24.jinja.ld) for more information. It is important to note that all regions we create in the loader should be 32-byte aligned since this is required for ARMv8.
The best way to see what variables are in each section is to view the app's `.Map` file in the build directory.

### Our FreeRTOS delta

We modify the [port.c](https://github.com/FreeRTOS/FreeRTOS-Kernel/blob/5a9d7c8388c32ff0bc530cd713f32e15c3e38d52/portable/GCC/ARM_CM33_NTZ/non_secure/port.c) file that ships with FreeRTOS. Our version can be found [here](../rtos/src/ports/port.c) and has some important changes. These changes are so that we can, when needed, run system calls from an unprivileged context.
We mark the following functions as *FREERTOS_SYSTEM_CALL* rather than the original *PRIVILEGED_FUNCTION*

    vPortEnterCritical
    vPortExitCritical
    xPortIsTaskPrivileged

It is important to note that `vPortSVCHandler_C` is used to temporarily elevate privileges. By default `mpu_wrappers_v1` allows for elevating privileges. This function will only elevate privileges if the SVC was raised from a function in the `freertos_system_calls` section.

Since [port.c](https://github.com/FreeRTOS/FreeRTOS-Kernel/blob/5a9d7c8388c32ff0bc530cd713f32e15c3e38d52/portable/GCC/ARM_CM33_NTZ/non_secure/port.c) is under continuous development, we pin the FreeRTOS version to ensure we have no build conflicts. If we want to move to a newer FreeRTOS version, it is important to ensure that we apply our needed changes to the latest [port.c](https://github.com/FreeRTOS/FreeRTOS-Kernel/blob/5a9d7c8388c32ff0bc530cd713f32e15c3e38d52/portable/GCC/ARM_CM33_NTZ/non_secure/port.c) file.

Here is some relevant FreeRTOS forum discussion that describes the workarounds we use:
- [Privilege escalation from outside of the kernel](https://forums.freertos.org/t/xportraiseprivilege-and-vportresetprivilege-from-outside-kernel/17925/2)
- [Enter/Exit critical section privilege escalation](https://forums.freertos.org/t/cortex-m33-and-mpu/17581/5)

These changes allow for the SYSCALLs described in the following section.

### Syscalls

Certain functions are desired to run as privileged even if the calling task is unprivileged. This is done by setting `configENFORCE_SYSTEM_CALLS_FROM_KERNEL_ONLY` as 0 in [FreeRTOSConfig.h](../../config/FreeRTOSConfig.h) and then escalating privileges from within the noted function.
These functions are placed in the `freertos_system_calls` region (globally RX) and then the section we want to run as privileged is wrapped in privilege escalation and privilege reset calls. The *in app* comment specifies that this function also exists in the bootloader. We want to keep the bootloader size minimal, so we need to ensure that only the app version has the FreeRTOS function imports.

    from_read: needed for delta fwup, privileged read of active slot
    secure_glitch_random_delay (in app)
    mcu_reset_with_reason (in app)
    mcu_systick_get_reload
    mcu_systick_get_value
    mcu_usart_tx_write

Warning that this list may become stale. Our list of syscalls can be found either by searching for the "SYSCALL" attribute in this repository or by viewing the contents of `freertos_system_calls` in the build map file and excluding third-party FreeRTOS system calls.

### FreeRTOS config values

There are a few FreeRTOS config values related to the MPU configuration that are worth calling out.

- **configPROTECTED_KERNEL_OBJECT_POOL_SIZE**: 256, sets maximum allocatable FreeRTOS kernel objects. This value should be tuned
- **configSYSTEM_CALL_STACK_SIZE**: 128, static buffer size that each task gets to execute system calls. Total memory usage is (configSYSTEM_CALL_STACK_SIZE * num_tasks) words
- **configTOTAL_MPU_REGIONS**: 16
- **configENFORCE_SYSTEM_CALLS_FROM_KERNEL_ONLY**: 0, we use the `SYSCALL` attribute for certain functions to be callable from the `freertos_system_calls` section
- **configALLOW_UNPRIVILEGED_CRITICAL_SECTIONS**: 1, exists in other FreeRTOS ports, we brought it over ourselves however
- **configUSE_MPU_WRAPPERS_V1**: 1, we're using v1 of the wrappers

### Debugging Memory Faults

When a crash happens and task isolation is suspected there are a couple helpful debugging breakpoints and memory addresses to inspect in GDB.

- Set breakpoints at: `memfault_fault_handler, MemManage_Handler, SMU_PRIVILEGED_IRQHandler, mcu_default_handler`
- Get current task name: `p pxCurrentTCB->pcTaskName`
- Get task's MPU regions: `p/x pxCurrentTCB->xMPUSettings.xRegionsSettings` (note the the bottom 5 bits of the address hold MPU region information. See `ARMv8-M MPU` under [Resources](#resources))
- View address where memory fault occurred: `x/4x *0xE000ED38`
- It is also helpful to view the `.Map` file under the build directory

### Future Improvements

There are a variety of improvements that can and should be made for MPU task isolation.

- IPC should be restuctured so that fewer compiler attributes and global buffer accesses need to be given (mempools are globally RW currently).
- A worker task could be made that initializes all RTOS kernel objects and then passes them into their respective tasks. This could simplify startup of some tasks and make it easier for more tasks to be unprivileged.
- MPU permissions should be revisited to ensure that we are giving least-privilege access to each task. (ex: can some peripherals be RO instead of RW?)
- Our changes to FreeRTOS's port.c (see [Our FreeRTOS Delta](#our-freertos-delta)) should be made into a patch file
- More tasks should be made unprivileged (namely Authentication and Key Matching tasks) (see [Task Table](#task-table))
- `configSYSTEM_CALL_STACK_SIZE` and `configPROTECTED_KERNEL_OBJECT_POOL_SIZE` from [FreeRTOS config value](#freertos-config-values) should be tuned down
- [mpu_regions.c](src/mpu_regions.c) can be simplified as a jinja file
- Better MPU region permission checking to ensure there are no regressions. [mpu_cmd.c](../../hal/mpu/src/mpu_cmd.c) currently does some basic checks, but this is only for privileged accesses and not task-specific unprivileged access.
- Static asserts on the size of each region to ensure we only have up to 11
