import struct
import gdb
import binascii

try:
    import msgpack
except:
    print("ERROR: msgpack not installed")
    print("ERROR: install with: python2.7 -m pip install msgpack")


class PrintMetadataCmd(gdb.Command):
    """Prints target metadata"""
    __HEADER_LENGTH = 6

    def __init__(self):
        super(PrintMetadataCmd, self).__init__(
            "pmeta", gdb.COMMAND_USER
        )

    def read_memory_addr(self, address, length):
        memory = gdb.selected_inferior().read_memory(address, length)
        return bytes(memory)

    def get_uin32_symbol_addr(self, symbol):
        return int(gdb.parse_and_eval(
            "(uint32_t){}".format(symbol)))

    def print_metadata(self, addr, name=""):
        # Read the header
        header = self.read_memory_addr(addr, self.__HEADER_LENGTH)
        crc, length = struct.unpack('IH', header)

        # Read the metadata bytes
        data = gdb.selected_inferior().read_memory(
            addr + self.__HEADER_LENGTH, length)
        meta = msgpack.unpackb(data)

        # Print the metadata key/values
        print("Metadata Info ({}):".format(name))
        for key, value in sorted(meta.items()):
            key_formatted = str(key).replace('_', ' ').title()
            value_formatted = str(value)
            if key == "hash":
                value_formatted = binascii.hexlify(value)
            print('  {:15s} {:15s}'.format(key_formatted, value_formatted))

        print("  {:15s} 0x{:4x}".format('CRC', crc))
        print("  {:15s} {}".format('Length', length))

    def invoke(self, arg, from_tty):
        if arg == 'a' or arg == "app":
            metadata_addr = self.get_uin32_symbol_addr('app_metadata_a_page')
            self.print_metadata(metadata_addr, "Application (A)")
            metadata_addr = self.get_uin32_symbol_addr('app_metadata_b_page')
            self.print_metadata(metadata_addr, "Application (B)")
        elif arg == 'b' or arg == "bl":
            metadata_addr = self.get_uin32_symbol_addr('bl_metadata_page')
            self.print_metadata(metadata_addr, "Bootloader")
        else:
            usage = "Usage:\n" \
                    "print firmware metadata stored in flash\n" \
                    "    a, app    print the application metadata\n" \
                    "    b, bl     print the bootloader metadata"
            print(usage)


PrintMetadataCmd()
