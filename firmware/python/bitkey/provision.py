from .mfgtest.cli import W1Controller
from .mfgtest.platform_config import W1A_PROTO0_CONFIG
import shlex
import sh

# Script for provisioning internal beta units.


class ProvisioningSession:
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


if __name__ == "__main__":
    ProvisioningSession().run()
