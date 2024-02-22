import nfc

from bitkey_proto import wallet_pb2 as wallet_pb
from .shell import Shell


class NFCTransaction:
    retry_max = 5
    rdwr_options = {'on-connect': lambda tag: False}
    timeout = 0.25  # 250ms timeout

    def __init__(self, usbstr='usb'):
        self.clf = nfc.ContactlessFrontend(usbstr)
        self._connect()

    def _connect(self):
        try:
            self.tag = self.clf.connect(rdwr=self.rdwr_options)
        except Exception as e:
            raise (e)
        if self.tag == False or self.tag == None:
            raise IOError("Failed to connect to NFC reader")

    def _resume(self):
        # ST-RFAL does not support APDU chaining during NFC activation.
        # To work around this, we send an empty message as the first thing
        # to resume communications.
        try:
            self.tag.transceive(Wca.make_resume_message())
        except Exception as e:
            print("failed to resume")
            raise (e)

    def transceive(self, payload, timeout=None):
        timeout = timeout if timeout else self.timeout
        for _ in range(0, self.retry_max):
            try:
                return self.tag.transceive(bytes(payload), timeout=timeout)
            except nfc.tag.tt4.Type4TagCommandError:
                print("NFC error, retrying...")
                self._connect()
                self._resume()
                continue
        raise IOError("nfc retry error")

    def close(self):
        self.clf.close()


class ShellTransaction:
    def __init__(self, port):
        self.shell = Shell(port)

    def transceive(self, payload, timeout=None):
        return self.shell.command_binary("wca", bytes(payload))

    def close(self):
        self.clf.close()


class Wca:
    CLA = 0x87
    INS_PROTO = 0x75
    INS_PROTO_CONT = 0x77
    INS_PROTO_GET_RESPONSE = 0x78

    MAX_WCA_BUFFER_SIZE = 512
    APDU_OVERHEAD_SIZE = 7
    MAX_PROTO_SIZE = MAX_WCA_BUFFER_SIZE - APDU_OVERHEAD_SIZE

    @staticmethod
    def _be_uint16(size):
        return size.to_bytes(2, byteorder='big')

    @staticmethod
    def _encode_lc(size):
        if size >= 1 and size <= 255:
            return size.to_bytes(1, byteorder='big')
        else:
            return [0] + list(size.to_bytes(2, byteorder='big'))

    @staticmethod
    def make_resume_message():
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.empty_cmd()
        cmd.empty_cmd.CopyFrom(msg)
        return bytes(Wca.from_serialized_proto(cmd.SerializeToString())[0])

    @staticmethod
    def from_serialized_proto(proto):
        first = True
        chunks = []
        for i in range(0, len(proto), Wca.MAX_PROTO_SIZE):
            proto_fragment = proto[i:i + Wca.MAX_PROTO_SIZE]

            if first:
                first = False
                chunk = [Wca.CLA, Wca.INS_PROTO]
            else:
                chunk = [Wca.CLA, Wca.INS_PROTO_CONT]

            chunk += Wca._be_uint16(len(proto))
            chunk += Wca._encode_lc(len(proto_fragment))
            chunk += proto_fragment

            assert len(chunk) <= Wca.MAX_WCA_BUFFER_SIZE
            chunks.append(chunk)
        return chunks


class WalletComms:
    def __init__(self, transport=None, debug=False):
        if transport == None:
            transport = NFCTransaction()
        self.transport = transport
        self.debug = debug

    def _status_words_ok(self, sw1, sw2):
        return (sw1 == 0x90 and sw2 == 0x00) or (sw1 == 0x61)

    def transceive(self, proto, timeout=None):
        serialized = proto.SerializeToString()
        chunks = Wca.from_serialized_proto(serialized)

        for chunk in chunks:
            if self.debug:
                print(f"TX: {', '.join('0x{:02x}'.format(a) for a in chunk)}")
            response = self.transport.transceive(chunk, timeout=timeout)
            if response is None or len(response) < 2:
                raise IOError("response error")
            sw1, sw2 = response[-2], response[-1]
            if not self._status_words_ok(sw1, sw2):
                raise IOError(f"apdu status words {sw1}, {sw2}")

        response_bytes = response[:len(response)-2]
        if self.debug:
            print(
                f"RX: {', '.join('0x{:02x}'.format(a) for a in response_bytes)}")

        # TODO: Handle response chunks
        rsp = wallet_pb.wallet_rsp()

        # TODO(W-4766): Determine why there is sometimes a garbage
        # first byte when spamming NFC messages.
        try:
            rsp.ParseFromString(bytes(response_bytes))
        except:
            response_bytes = response_bytes[1:]
            rsp.ParseFromString(bytes(response_bytes))

        return rsp

    def close(self):
        self.transport.close()
