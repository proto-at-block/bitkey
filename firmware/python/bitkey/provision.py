from .mfgtest.cli import W1Controller
from .mfgtest.platform_config import W1A_PROTO0_CONFIG
from .wallet import Wallet, WalletComms
from .comms import NFCTransaction
import shlex
import sh
import sys

# Script for provisioning internal beta units.


class InternalBetaProvisioningSession:
    def __init__(self):
        self.w1 = W1Controller(config=W1A_PROTO0_CONFIG)

    def run(self):
        assy, mlb = self.record_serials()
        input(
            "Build and flash (with -e) mfgtest firmware **WITH BIO_DEV_MODE=true**, then hit enter...\n\n")

        self.provision_fpc_key()
        self.write_serials(assy, mlb)

        print("Double checking serials: ")
        self.record_serials()

        self.enable_secure_boot()

        input(
            "Build and flash the main app image, then hit enter...\n\n")

        print("Done")

    def record_serials(self):
        identifiers = self.w1.identifiers()
        assy, mlb = identifiers.assy_serial, identifiers.mlb_serial
        print(f"assy: {assy}")
        print(f"mlb: {mlb}")
        return assy, mlb

    def provision_fpc_key(self):
        shell = self.w1.shell
        out = shell.command("fpc -p 0")
        print(out)

    def write_serials(self, assy, mlb):
        self.w1.write_assy_serial(assy)
        self.w1.write_mlb_serial(mlb)

    def enable_secure_boot(self):
        cmd = sh.Command(
            "/Applications/Commander.app/Contents/MacOS/commander")
        cmd(shlex.split(
            "security writekey --sign config/keys/w1a-dev/w1a-root-firmware-signing-ca-key-dev.pub.pem --device EFR32MG24B310F1536IM48"),
            _fg=True)
        cmd(shlex.split(
            "security writeconfig --nostore --configfile config/keys/w1a-dev/user_configuration.json --device EFR32MG24B310F1536IM48"),
            _fg=True)

class FingerprintExperimentProvisioningSession:
    def __init__(self):
        self.w1  = Wallet(WalletComms(NFCTransaction()))

    def ensure_is_dev(self):
        output = sh.inv("secinfo", "-v")
        dev_pubkey = "A95750D30EDDFB969DE69642753853CCD7D5758ECC6EA2BD6FB0DAF06E5139F168F777B047948591D77079611884F62553D76B769F5257F1D8B01E0F8FE49B13"
        assert dev_pubkey in output, "Device public key not found in secinfo output"

    def record_serial(self, serial):
        with open("serials.txt", "a") as f:
            f.write(f"{serial}\n")

    def run(self):
        # self.ensure_is_dev()

        serial = self.w1.device_id().device_id_rsp.assy_serial
        self.record_serial(serial)

        print("Applying release")
        sh.inv("release.apply", "-v", "1.0.65", _out=sys.stdout)

        print("Start fingerprint enrollment")
        self.w1.start_fingerprint_enrollment()

if __name__ == "__main__":
    # InternalBetaProvisioningSession().run()
    FingerprintExperimentProvisioningSession().run()
