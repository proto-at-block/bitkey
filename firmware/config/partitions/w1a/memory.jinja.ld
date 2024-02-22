MEMORY
{
{% for m in memory %}
  {{ m }}
{% endfor %}
}

SECTIONS
{
    .noinit (NOLOAD) :
    {
        KEEP(*(*.noinit*))
    } > RAM_NOINIT

    /* bootloader info */
    .bl_metadata_section {% if not bootloader %}(NOLOAD){% endif %}:
    {
        KEEP(*(.bl_metadata_section))
    } > FLASH_BOOTLOADER_METADATA

    /* application metadata */
    .app_a_metadata_section {% if slot != 'a' %}(NOLOAD){% endif %}:
    {
        KEEP(*(.app_a_metadata_section))
    } > FLASH_APPLICATION_A_METADATA

    /* application metadata */
    .app_b_metadata_section {% if slot != 'b' %}(NOLOAD){% endif %}:
    {
        KEEP(*(.app_b_metadata_section))
    } > FLASH_APPLICATION_B_METADATA

{% if bootloader %}
    .bl_codesigning_signature_section :
    {
        . = ALIGN(4);
        KEEP(*(.bl_codesigning_signature_section))
    } > FLASH_BOOTLOADER_CODESIGN_SIGNATURE

{% else %}
    /* flash filesystem */
    .flash_fs_section (NOLOAD):
    {
        *(.flash_fs_section)
    } > FLASH_FILESYSTEM

    .app_a_properties_section {% if slot != 'a' %}(NOLOAD){% endif %}:
    {
        . = ALIGN(4);
        KEEP(*(.app_a_properties_section))
    } > FLASH_APPLICATION_A_PROPERTIES

    .app_a_codesigning_signature_section {% if slot != 'a' %}(NOLOAD){% endif %}:
    {
        . = ALIGN(4);
        KEEP(*(.app_a_codesigning_signature_section))
    } > FLASH_APPLICATION_A_CODESIGN_SIGNATURE

    .app_b_properties_section {% if slot != 'b' %}(NOLOAD){% endif %}:
    {
        . = ALIGN(4);
        KEEP(*(.app_b_properties_section))
    } > FLASH_APPLICATION_B_PROPERTIES

    .app_b_codesigning_signature_section {% if slot != 'b' %}(NOLOAD){% endif %}:
    {
        . = ALIGN(4);
        KEEP(*(.app_b_codesigning_signature_section))
    } > FLASH_APPLICATION_B_CODESIGN_SIGNATURE

    /* fixed place libraries */
{% for lib in libraries %}{{ lib }}{% endfor %}
{% endif %}

}

PROVIDE(__application_a_boot_addr = ORIGIN(FLASH_APPLICATION_A_PROGRAM));
PROVIDE(__application_a_boot_size = LENGTH(FLASH_APPLICATION_A_PROGRAM));
PROVIDE(__application_b_boot_addr = ORIGIN(FLASH_APPLICATION_B_PROGRAM));
PROVIDE(__application_b_boot_size = LENGTH(FLASH_APPLICATION_B_PROGRAM));

PROVIDE(__FLASH_segment_start__ = ORIGIN(FLASH_BOOTLOADER_PROGRAM));
PROVIDE(__FLASH_segment_end__ = ORIGIN(FLASH_BOOTLOADER_PROGRAM) + 1536K);

PROVIDE(flash_filesystem_addr = ORIGIN(FLASH_FILESYSTEM));
PROVIDE(flash_filesystem_size = LENGTH(FLASH_FILESYSTEM));
PROVIDE(ram_addr = ORIGIN(RAM));
PROVIDE(ram_size = LENGTH(RAM));
PROVIDE(__SRAM_segment_start__ = ORIGIN(RAM));
PROVIDE(__SRAM_segment_end__ = __SRAM_segment_start__ + LENGTH(RAM));

PROVIDE(__application_a_signature_addr = ORIGIN(FLASH_APPLICATION_A_CODESIGN_SIGNATURE));
PROVIDE(__application_a_signature_size = LENGTH(FLASH_APPLICATION_A_CODESIGN_SIGNATURE));
PROVIDE(__application_a_properties_addr = ORIGIN(FLASH_APPLICATION_A_PROPERTIES));
PROVIDE(__application_a_properties_size = LENGTH(FLASH_APPLICATION_A_PROPERTIES));
PROVIDE(__application_a_start_addr = ORIGIN(FLASH_APPLICATION_A_METADATA));
PROVIDE(__application_a_signing_size = LENGTH(FLASH_APPLICATION_A_PROPERTIES) + LENGTH(FLASH_APPLICATION_A_METADATA) + LENGTH(FLASH_APPLICATION_A_PROGRAM));
PROVIDE(__application_b_signature_addr = ORIGIN(FLASH_APPLICATION_B_CODESIGN_SIGNATURE));
PROVIDE(__application_b_signature_size = LENGTH(FLASH_APPLICATION_B_CODESIGN_SIGNATURE));
PROVIDE(__application_b_properties_addr = ORIGIN(FLASH_APPLICATION_B_PROPERTIES));
PROVIDE(__application_b_properties_size = LENGTH(FLASH_APPLICATION_B_PROPERTIES));
PROVIDE(__application_b_start_addr = ORIGIN(FLASH_APPLICATION_B_METADATA));
PROVIDE(__application_b_signing_size = LENGTH(FLASH_APPLICATION_B_PROPERTIES) + LENGTH(FLASH_APPLICATION_B_METADATA) + LENGTH(FLASH_APPLICATION_B_PROGRAM));

{% if not bootloader %}

    /* Must match `enum firmware_slot` in wallet.proto. */
    {% if slot == 'a' %}
    PROVIDE(active_slot = 1);
    {% elif slot == 'b' %}
    PROVIDE(active_slot = 2);
    {% endif %}

{% endif %}

{% include 'efr32mg24.jinja.ld' %}
