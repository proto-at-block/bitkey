REGION_ALIAS("REGION_TEXT",     {{ program_section }});
REGION_ALIAS("REGION_RODATA",   {{ program_section }});
REGION_ALIAS("REGION_DATA_ROM", {{ program_section }});
REGION_ALIAS("REGION_FILL",     {{ program_section }});
REGION_ALIAS("REGION_DATA",   RAM);
REGION_ALIAS("REGION_BSS",    RAM);
REGION_ALIAS("REGION_HEAP",   RAM);
REGION_ALIAS("REGION_STACK",  RAM);

ENTRY(mcu_reset_handler)

_Min_Heap_Size = 0x200;
_Min_Stack_Size = 0x400;

SECTIONS
{
  /* metadata flash page */
  bl_metadata_page = ORIGIN(FLASH_BOOTLOADER_METADATA);
  bl_metadata_size = LENGTH(FLASH_BOOTLOADER_METADATA);
  app_a_metadata_page = ORIGIN(FLASH_APPLICATION_A_METADATA);
  app_a_metadata_size = LENGTH(FLASH_APPLICATION_A_METADATA);
  app_b_metadata_page = ORIGIN(FLASH_APPLICATION_B_METADATA);
  app_b_metadata_size = LENGTH(FLASH_APPLICATION_B_METADATA);

  /* slot addresses and sizes */
  app_a_slot_page = ORIGIN(FLASH_APPLICATION_A_METADATA);
  app_b_slot_page = ORIGIN(FLASH_APPLICATION_B_METADATA);
  app_a_signature = ORIGIN(FLASH_APPLICATION_A_CODESIGN_SIGNATURE);
  app_b_signature = ORIGIN(FLASH_APPLICATION_B_CODESIGN_SIGNATURE);
  app_slot_size = LENGTH(FLASH_APPLICATION_A_METADATA) + LENGTH(FLASH_APPLICATION_A_PROPERTIES) + LENGTH(FLASH_APPLICATION_A_PROGRAM) + LENGTH(FLASH_APPLICATION_A_CODESIGN_SIGNATURE);
  bl_slot_size = LENGTH(FLASH_BOOTLOADER_METADATA) + LENGTH(FLASH_BOOTLOADER_PROGRAM) + LENGTH(FLASH_BOOTLOADER_CODESIGN_SIGNATURE);
  bl_base_addr = ORIGIN(FLASH_BOOTLOADER_PROGRAM);
  bl_program_size = LENGTH(FLASH_BOOTLOADER_PROGRAM);
  bl_signature_addr = ORIGIN(FLASH_BOOTLOADER_CODESIGN_SIGNATURE);
  bl_signature_size = LENGTH(FLASH_BOOTLOADER_CODESIGN_SIGNATURE);

  .isr_vector :
  {
    . = ALIGN(8);
    KEEP(*(.isr_vector)) /* Startup code */
    . = ALIGN(8);
  } > REGION_TEXT

  .text :
  {
    . = ALIGN(8);

    /* privileged functions */
    . = ALIGN(32);
    PROVIDE(__privileged_functions_start__ = .);
    *(privileged_functions)
    . = ALIGN(32);
    PROVIDE(__privileged_functions_end__ = . - 1);
    /* end privileged functions */

    /* freertos syscalls */
    . = ALIGN(32);
    PROVIDE(__syscalls_flash_start__ = .);
    *(freertos_system_calls)
    . = ALIGN(32);
    PROVIDE(__syscalls_flash_end__ = . - 1);
    /* end freertos syscalls */

    /* start rest of flash */
    . = ALIGN(32);
    PROVIDE(__unprivileged_flash_start__ = .);

    /* Program code */
    *(.text)
    *(.text*)

    KEEP (*(.init))
    KEEP (*(.fini))

    /* Shell commands */
    . = ALIGN(4);
    __shell_cmds_start = .;
    KEEP(*(.shell_cmds.register))
    . = ALIGN(4);
    __shell_cmds_end   = .;

    KEEP(*(.eh_frame*))
  } > REGION_TEXT

  log_fmt 0xF0000000 (INFO):
  {
    __start_log_fmt = ABSOLUTE(.);
    KEEP(*(*.log_fmt_hdr))
    KEEP(*(log_fmt))
  }

  /* -funwind-tables */
  __exidx_start = .;
  .ARM.exidx :
  {
    *(.ARM.exidx* .gnu.linkonce.armexidx.*)
  } > REGION_TEXT
  __exidx_end = .;

  .rodata :
  {
    . = ALIGN(4);

    /* Read only data */
    *(.rodata .rodata.*)

    . = ALIGN(32);
    PROVIDE(__unprivileged_flash_end__ = . - 1);
    /* end of text + rodata */
  } > REGION_RODATA

  __etext = .;

  /* Used by the startup to initialize data */
  _sidata = LOADADDR(.data);

  /* Non-const initialised data */
  .data :
  {
    __data_start__ = .;

    . = ALIGN(8);
    _sdata = .;        /* STM32 specific */

    . = ALIGN(32);
    PROVIDE(__privileged_sram_start__ = .);
    __memfault_capture_freertos_start = .;
    *(privileged_data);
    __memfault_capture_freertos_end = .;
    . = ALIGN(32);
    PROVIDE(__privileged_sram_end__ = . - 1);

    /* display task data (includes shared task due to limited MPU regions) */
    . = ALIGN(32);
    PROVIDE(__display_task_data_start__ = .);

    /* usart task data (includes shared task due to limited MPU regions) */
    . = ALIGN(32);
    PROVIDE(__usart_task_data_start__ = .);

    /* shared data (across tasks/threads */
    . = ALIGN(32);
    PROVIDE(__fwup_task_data_start__ = .); /* sharing this region to save MPU regions for FWUP */
    PROVIDE(__shared_task_data_start__ = .);
    *(.shared_task_data*)
    *(.peripherals_data*) /* peripheral data */
    *platform.c.o(.data .data*) /* peripherals data */
    *lib_a-impure.o(.data .data*) /* peripherals data */
    *libmcu-stm32*(.data .data*) /* peripherals data */
    . = ALIGN(32);
    PROVIDE(__shared_task_data_end__ = .);
    /* end shared data */

    *(.fwup_task_data*)
    . = ALIGN(32);
    PROVIDE(__fwup_task_data_end__ = .);
    /* end FWUP task data */

    *(.usart_task_data*)
    . = ALIGN(32);
    PROVIDE(__usart_task_data_end__ = .);
    /* end usart data */

    *(.display_task_data*)
    . = ALIGN(32);
    PROVIDE(__display_task_data_end__ = .);
    /* end display data */

    *(.data)
    *(.data*)

    . = ALIGN(4);
    /* preinit data */
    PROVIDE_HIDDEN (__preinit_array_start = .);
    KEEP(*(.preinit_array))
    PROVIDE_HIDDEN (__preinit_array_end = .);

    . = ALIGN(4);
    /* init data */
    PROVIDE_HIDDEN (__init_array_start = .);
    KEEP(*(SORT(.init_array.*)))
    KEEP(*(.init_array))
    PROVIDE_HIDDEN (__init_array_end = .);

    . = ALIGN(4);
    /* finit data */
    PROVIDE_HIDDEN (__fini_array_start = .);
    KEEP(*(SORT(.fini_array.*)))
    KEEP(*(.fini_array))
    PROVIDE_HIDDEN (__fini_array_end = .);

    KEEP(*(.jcr*))

    /* ramfuncs */
    . = ALIGN(32);
    PROVIDE(__ramfunc_start__ = .);
    *(.ram*)
    . = ALIGN(32);
    PROVIDE(__ramfunc_end__ = .);

    . = ALIGN(8);
    _edata = .;        /* STM32 specific */
  } > REGION_DATA AT> REGION_DATA_ROM

  /* fills the unused program flash with 0xff because we sign the full slot */
  .fill :
  {
    _pad_start = .;
    FILL(0xff);

{% if bootloader %}
    . += ORIGIN(FLASH_BOOTLOADER_METADATA) - _pad_start - 1;
{% else %}
{% if slot == 'a' %}
    . += ORIGIN(FLASH_APPLICATION_A_CODESIGN_SIGNATURE) - _pad_start - 1;
{% elif slot == 'b' %}
    . += ORIGIN(FLASH_APPLICATION_B_CODESIGN_SIGNATURE) - _pad_start - 1;
{% endif %}
{% endif %}

    /*
    This byte is needed so this section is non-empty without the padding.
    One byte must be subtracted from the cursor move above to account for this byte.
    */
    BYTE(0xff);
  } > REGION_FILL

  /* Section to place initialized data to be captured to Memfault */
  .memfault_capture_data :
  {
    __memfault_capture_data_start = .;
    *(.memfault_capture_data*)
    __memfault_capture_data_end = .;

    . = ALIGN(4);
    /* All data end */
    __data_end__ = .;
  } > REGION_DATA

  /* Ditto, but for BSS */
  .memfault_capture_bss :
  {
    __memfault_capture_bss_start = .;
    *(.memfault_capture_bss*)
    __memfault_capture_bss_end = .;
  } > REGION_BSS

  /* Uninitialised data (BSS + commons) */
  . = ALIGN(8);
  .bss (NOLOAD) :
  {
    _sbss = .;         /* STM32 specific */
    __bss_start__ = _sbss;

    /* FWUP bss */
    . = ALIGN(32);
    PROVIDE(__fwup_task_bss_start__ = .);
    *src_fwup_delta.c.o(.bss COMMON .bss*)

    /* start shared task bss */
    . = ALIGN(32);
    PROVIDE(__shared_task_bss_start__ = .);
    *(.shared_task_bss*)

    *filesystem.c.o(.bss COMMON .bss*)
    *libmcu-stm32*(.bss COMMON .bss*) /* peripherals bss */

    *memfault-firmware-sdk_components*.o(.bss COMMON .bss*)
    *memfault-firmware-sdk_ports*.o(.bss COMMON .bss*)

    . = ALIGN(32);
    PROVIDE(__shared_task_bss_end__ = .);
    /* end shared task bss data */

    *src_fwup.c.o(.bss COMMON .bss*)
    . = ALIGN(32);
    PROVIDE(__fwup_task_bss_end__ = .); /* overlapping to save the MPU region */

    /* shared task ro for bss (ro access for tasks) */
    . = ALIGN(32);
    PROVIDE(__shared_task_protected_start__ = .);
    *lib_a-stack_protector.o(.bss COMMON .bss*)
    . = ALIGN(32);
    PROVIDE(__shared_task_protected_end__ = .);

    *(.bss)
    *(.bss*)
    *(COMMON)

    . = ALIGN(8);
    . = ALIGN(4);
    _ebss = .;         /* STM32 specific */
    __bss_end__ = _ebss;
    PROVIDE( __bss_end = .);
  } > REGION_BSS

  PROVIDE( __bss_start = __bss_start__ );
  PROVIDE( __bss_size = __bss_end - __bss_start );

  .heap (COPY):
  {
    __HeapBase = .;
    __end__ = .;
    end = __end__;
    _end = __end__;
    KEEP(*(.heap*))
    . = . + _Min_Heap_Size;
    __HeapLimit = .;
  } > REGION_HEAP

  /* .stack_dummy section doesn't contains any symbols. It is only
   * used for linker to calculate size of stack sections, and assign
   * values to stack symbols later */
  .stack_dummy (COPY):
  {
    KEEP(*(.stack*))
    . = . + _Min_Stack_Size;
  } > REGION_STACK

  /* Set stack top to end of RAM, and stack limit move down by
   * size of stack_dummy section */
  __StackTop = ORIGIN(RAM) + LENGTH(RAM);
  __StackLimit = __StackTop - SIZEOF(.stack_dummy);
  PROVIDE(__stack = __StackTop);
  
  /* STM32 specific stack pointer - keep for compatibility */
  _estack = __StackTop;

  /* Check if data + heap + stack exceeds RAM limit */
  ASSERT(__StackLimit >= __HeapLimit, "region RAM overflowed with stack")

  /* SRAM4 section for STM32U5 specific use */
  .sram4 (NOLOAD) :
  {
    . = ALIGN(4);
    *(.sram4)
    *(.sram4*)
    . = ALIGN(4);
  } > SRAM4

  /* Remove information from the compiler libraries */
  /DISCARD/ :
  {
    libc.a:* ( * )
    libm.a:* ( * )
    libgcc.a:* ( * )
  }

  .ARM.attributes 0 : { *(.ARM.attributes) }
}
