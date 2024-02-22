# Metadata

This library provides support for generating, building and reading firmware related metadata, such as `git` branch/identity, version, build type and build time.

## Structure

Metadata is packed into a self-describing binary format using [MessagePack](https://msgpack.org/). MessagePack allows for expansion and evolution of the metadata over time compared to packed C `struct`'s.

The binary MessagePack data is combined with a header containing a CRC32 of the data and a length field to allow validation and decoding by the embedded device.

```text
+-------------------+-------------------------+
| Header[0:5]       | Data[6:]                |
+----------+--------+-------------------------+
| Checksum | Length | Binary MessagePack data |
+----------+--------+-------------------------+

Checksum (uint32_t) : CRC32 of Data
Length   (uint16_t) : Length of Data
```

### Building

To build a firmware with embedded metadata, the following tasks need to be integrated into the build system.

1. Build the `firmware.elf`
2. Create a `firmware.bin`
3. Generate the `metadata.bin`
4. Add the metadata into the original `firmware.elf`

For example:

```bash
inv build
arm-none-eabi-objcopy -O binary firmware.elf firmware.bin
inv meta --generate -i firmware.bin -o metadata.bin
arm-none-eabi-objcopy --update-section .app_metadata_section=metadata.bin firmware.elf firmware_with_metadata.elf
```

## API Support

### Python

A Python class `Metadata` is used to generate or decode the metadata binary data.

The example below will generate a metadata binary `metadata.bin` for the firmware binary `firmware.bin`.

```python
m = Metadata("firmware.bin")
m.generate("metadata.bin", build_type="Debug")
```

### C (Embedded)

The C API provides support for reading, validating and printing metadata embedded in the firmware.

```c
metadata_result_t metadata_get(metadata_target_t target, metadata_t* meta);
metadata_result_t metadata_validity(metadata_target_t target);
void metadata_print(metadata_target_t target);
```

## GDB Support

A script is provided that adds a `pmeta` command to GDB, which will print the metadata information of the attached target containing metadata.

To start GDB with this command added, run:

```bash
arm-none-eabi-gdb-py -q firmware.elf --ex=\"source lib/metadata/gdb.py\"
```

Once in the GDB shell, use the `pmeta` command, for example:

```text
(gdb) pmeta
Metadata Info:
  Build           Debug          
  Git Branch      gussy/new-feature 
  Git Id          v0.0.1-7-g55ae50d5-dirty
  Hash            f89b6af29a86d0f45245cbda57fc969ec6924ee6e2916581b4620cb698f61fb8
  Timestamp       1653335345     
  Ver Major       0              
  Ver Minor       0              
  Ver Patch       1              
  CRC             0x681cca14
  Length          158
```
