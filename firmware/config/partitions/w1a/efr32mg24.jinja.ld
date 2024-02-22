REGION_ALIAS("REGION_TEXT",     {{ program_section }});
REGION_ALIAS("REGION_RODATA",   {{ program_section }});
REGION_ALIAS("REGION_DATA_ROM", {{ program_section }});
REGION_ALIAS("REGION_FILL",     {{ program_section }});
REGION_ALIAS("REGION_DATA",   RAM);
REGION_ALIAS("REGION_BSS",    RAM);
REGION_ALIAS("REGION_HEAP",   RAM);
REGION_ALIAS("REGION_STACK",  RAM);

ENTRY(mcu_reset_handler)

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

  .text :
  {
    KEEP(*(.vectors))
    __Vectors_End = .;
    __Vectors_Size = __Vectors_End - __Vectors;
    __end__ = .;

{% if not bootloader %}
    . = ALIGN(512);
    KEEP(*(.vectors_real))
    __VectorsReal_End = .;
    __VectorsReal_Size = __VectorsReal_End - __VectorsReal;
    __end__ = .;
{% endif %}

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
    *(.text*)

    KEEP(*(.init))
    KEEP(*(.fini))

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
    /*
    Note: binutils >= 0.29 will automatically create this symbol but we set
    it explicitly for compatibility with older versions
    */
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
    KEEP(*(.rodata.keep .rodata.keep.*))

    /* Read only data */
    *(.rodata .rodata.*)

    . = ALIGN(32);
    PROVIDE(__unprivileged_flash_end__ = . - 1);
    /* end of text + rodata */
  } > REGION_RODATA

  __etext = .;

  /* Non-const initialised data */
  .data :
  {
    __data_start__ = .;

    . = ALIGN(32);
    PROVIDE(__privileged_sram_start__ = .);
    __memfault_capture_freertos_start = .;
    *(privileged_data);
    __memfault_capture_freertos_end = .;
    . = ALIGN(32);
    PROVIDE(__privileged_sram_end__ = . - 1);

    /* shared data (across tasks/threads */
    . = ALIGN(32);
    PROVIDE(__fwup_task_data_start__ = .); /* sharing this region saves 1 mpu region for fwup */
    PROVIDE(__shared_task_data_start__ = .);
    *(.shared_task_data*)
    *(.peripherals_data*) /* peripheral data */
    *platform.c.o(.data .data*) /* peripherals data */
    *lib_a-impure.o(.data .data*) /* peripherals data */
    *libmcu-efr32xg24*(.data .data*) /* peripherals data */
    . = ALIGN(32);
    PROVIDE(__shared_task_data_end__ = .);
    /* end shared data */

    /* fwup task data */
    . = ALIGN(32);
    *(.fwup_task_data*)
    . = ALIGN(32);
    PROVIDE(__fwup_task_data_end__ = .);
    /* end fwup task data */

    /* nfc task data */
    . = ALIGN(32);
    PROVIDE(__nfc_task_data_start__ = .);
    *(.nfc_task_data*)
    . = ALIGN(32);
    PROVIDE(__nfc_task_data_end__ = .);
    /* end shared data */

    /* led task data */
    . = ALIGN(32);
    PROVIDE(__led_task_data_start__ = .);
    *(.led_task_data*)
    *src_fpc_biometrics.c.o(.data .data*)
    . = ALIGN(32);
    PROVIDE(__led_task_data_end__ = .);
    /* end led data */

    *(vtable)
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
    /* end ramfuncs */

    __data_non_const_end__ = .;
  } > REGION_DATA AT > REGION_DATA_ROM

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
  .bss (NOLOAD) :
  {
    . = ALIGN(4);

    _sbss = .;

    __bss_start__ = _sbss;

    /* nfc data */
    . = ALIGN(32);
    PROVIDE(__nfc_task_bss_start__ = .);
    *st-rfal_source*.o(.bss COMMON .bss*)
    . = ALIGN(32);
    PROVIDE(__nfc_task_bss_end__ = .);
    /* end nfc data */

    /* auth matching data */
    . = ALIGN(32);
    PROVIDE(__auth_matching_task_bss_start__ = .);
    *fpc_bio_internal_context.o(.bss COMMON .bss*)
    *fpc_sensor_internal_context.o(.bss COMMON .bss*)
    *platform.c.o(.bss COMMON .bss*) /* this is the ucHeap */
    . = ALIGN(32);
    PROVIDE(__auth_matching_task_bss_end__ = .);
    /* end auth matching data */

    /* fwup data */
    . = ALIGN(32);
    PROVIDE(__fwup_task_bss_start__ = .);
    *src_fwup_delta.c.o(.bss COMMON .bss*)
    . = ALIGN(32);
    /* end fwup data */

    /* start shared task bss */
    PROVIDE(__shared_task_bss_start__ = .);
    *(.shared_task_bss*)

    *filesystem.c.o(.bss COMMON .bss*)
    *src_sl_se_manager.c.o(.bss COMMON .bss*)
    *libmcu-efr32xg24*(.bss COMMON .bss*) /* peripherals bss */

    *memfault-firmware-sdk_components*.o(.bss COMMON .bss*)
    *memfault-firmware-sdk_ports*.o(.bss COMMON .bss*)

    . = ALIGN(32);
    PROVIDE(__shared_task_bss_end__ = .);
    *src_fwup.c.o(.bss COMMON .bss*)
    . = ALIGN(32);
    PROVIDE(__fwup_task_bss_end__ = .); /* overlapping this region saves 1 mpu region for fwup */
    /* end shared task bss data */
    /* end fwup task bss */

    /* shared task ro for bss (ro access for tasks) */
    . = ALIGN(32);
    PROVIDE(__shared_task_protected_start__ = .);
    *lib_a-stack_protector.o(.bss COMMON .bss*)
    . = ALIGN(32);
    PROVIDE(__shared_task_protected_end__ = .);



    *(.bss*)
    *(COMMON)

    . = ALIGN(4);
    __bss_end__ = .;
  } > REGION_BSS

  .heap (COPY):
  {
    __HeapBase = .;
    __end__ = .;
    end = __end__;
    _end = __end__;
    KEEP(*(.heap*))
    __HeapLimit = .;
  } > REGION_HEAP

  /* .stack_dummy section doesn't contains any symbols. It is only
   * used for linker to calculate size of stack sections, and assign
   * values to stack symbols later */
  .stack_dummy (COPY):
  {
    KEEP(*(.stack*))
  } > REGION_STACK

  /* Set stack top to end of RAM, and stack limit move down by
   * size of stack_dummy section */
  __StackTop = ORIGIN(RAM) + LENGTH(RAM);
  __StackLimit = __StackTop - SIZEOF(.stack_dummy);
  PROVIDE(__stack = __StackTop);

  /* Check if data + heap + stack exceeds RAM limit */
  ASSERT(__StackLimit >= __HeapLimit, "region RAM overflowed with stack")
}
