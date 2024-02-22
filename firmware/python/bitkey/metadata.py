import os
import json
import time
import struct
import msgpack
import hashlib
import binascii

from bitkey import fw_version


class Metadata:
    _HEADER_LENGTH = 6  # 4 byte CRC then 2 byte length
    _HASH_BLOCK_SIZE = 65536
    _APP_OFFSET = 1024

    _MAX_GIT_STR_LEN = 64

    def __init__(self, binfile) -> None:
        self.binfile = binfile

    @staticmethod
    def read_from_bytes(meta: bytes):
        header = meta[:Metadata._HEADER_LENGTH]

        _, length = struct.unpack('IH', header)
        assert length != 0 and length <= Metadata._APP_OFFSET

        packed = meta[Metadata._HEADER_LENGTH:]
        assert length == len(packed)

        return msgpack.unpackb(packed, raw=False)

    def load(self) -> dict:
        if not os.path.exists(self.binfile):
            return {}

        with open(self.binfile, 'rb') as f:
            header = f.read(self._HEADER_LENGTH)

            _, length = struct.unpack('IH', header)

            if length == 0 or length > self._APP_OFFSET:
                return {}

            packed = f.read(length)
            return msgpack.unpackb(packed, raw=False)

    @property
    def max_length(self):
        return self._APP_OFFSET

    @property
    def length(self):
        length = 0

        if os.path.exists(self.binfile):
            return 0

        with open(self.binfile, 'rb') as f:
            header = f.read(self._HEADER_LENGTH)

            _, length = struct.unpack('IH', header)

            if length == 0 or length > self._APP_OFFSET:
                return 0

            return length

    def __hash_file(self):
        file_hash = hashlib.sha1()
        at_beginning = True

        if not os.path.exists(self.binfile):
            return ''

        with open(self.binfile, 'rb') as f:
            if at_beginning:
                f.seek(self._APP_OFFSET)
                at_beginning = False
            fb = f.read(self._HASH_BLOCK_SIZE)
            while len(fb) > 0:
                file_hash.update(fb)
                fb = f.read(self._HASH_BLOCK_SIZE)

        return file_hash.digest()

    def __data(self, build_type="", hw_rev="", image_type="") -> dict:
        data = fw_version.metadata(image_type)
        data["timestamp"] = int(time.time())
        data["hash"] = self.__hash_file()

        # Check length of all strings in metadata
        if "git_id" in data and len(data["git_id"]) > self._MAX_GIT_STR_LEN:
            print("git id too long. Truncating.")
            data["git_id"] = data["git_id"][:self._MAX_GIT_STR_LEN]
        if "git_branch" in data and len(data["git_branch"]) > self._MAX_GIT_STR_LEN:
            print("git branch too long. Truncating.")
            data["git_branch"] = data["git_branch"][:self._MAX_GIT_STR_LEN]

        return data

    def __write(self, out_file, data=None, image_type="") -> None:
        data = {**data, **self.__data(image_type=image_type)}
        binary = msgpack.packb(data, use_bin_type=True)

        # Calculate the CRC32 of the binary metadata
        crc32 = binascii.crc32(binary)

        # Write out the binary metadata file
        with open(out_file, 'wb') as f:
            # First 4 bytes are the CRC32
            f.write(struct.pack('I', crc32))

            # Next 2 bytes are the length of the messagepack data
            f.write(struct.pack('H', len(binary)))

            # Rest of the bytes are the messagepack data
            f.write(binary)

    def __write_json(self, out_file, data=None, image_type="") -> None:
        data = {**data, **self.__data(image_type=image_type)}

        # JSON doesn't support binary types
        data["hash"] = bytes(data["hash"]).decode("latin-1")

        # Write out the binary metadata file
        with open(out_file, 'w') as f:
            json.dump(data, f)
            f.close()

    def generate(self, out_file, json_out=False, data={}, image_type=""):
        if json_out:
            self.__write_json(out_file, data, image_type)
        else:
            self.__write(out_file, data, image_type)
