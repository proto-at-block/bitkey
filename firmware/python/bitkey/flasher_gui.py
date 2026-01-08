from gooey import Gooey, GooeyParser
import tempfile
from bitkey.gdb import JLinkGdbServer, gdb_flash_txt
from pathlib import Path


@Gooey(clear_before_run=True)
def main():
    """GUI flashing tool. Only works on MacOS.

    Build with `yes | pyinstaller build.spec` (from one dir above).
    The .app is placed in `dist/`.
    """

    parser = GooeyParser()
    parser.add_argument('--bootloader', widget="FileChooser",
                        help="Example: 'w1a-evt-loader-dev.signed.elf'",
                        required=True)
    parser.add_argument('--application-a', widget="FileChooser",
                        help="Example: 'w1a-evt-app-a-dev.signed.elf'",
                        required=True)
    parser.add_argument('--application-b', widget="FileChooser",
                        help="Example: 'w1a-evt-app-b-dev.signed.elf.' Optional.",
                        required=False)
    parser.add_argument('--chip', widget='Dropdown',
                        choices=['EFR32MG24BXXXF1536'], default='EFR32MG24BXXXF1536',
                        help="Example: 'EFR32MG24BXXXF1536' Optional.",
                        required=False)
    args = parser.parse_args()

    temp = tempfile.NamedTemporaryFile()
    with open(temp.name, 'w+') as f:
        f.write(gdb_flash_txt)

    print("Flashing begin.")

    # The hardcoded paths are the default install locations on MacOS.
    with JLinkGdbServer(args.chip, temp.name,
                        "/usr/local/bin/JLinkGDBServer",
                        "/Applications/ARM/bin/arm-none-eabi-gdb") as gdb:
        if not gdb.flash(Path(args.bootloader)):
            raise Exception("Failed to flash bootloader.")
        if not gdb.flash(Path(args.application_a)):
            raise Exception("Failed to flash application a.")
        if args.application_b and not gdb.flash(Path(args.application_b)):
            raise Exception("Failed to flash application b.")


if __name__ == "__main__":
    main()
