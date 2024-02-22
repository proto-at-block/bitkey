import logging
import re
import allure
import subprocess
from python.automation.conftest import PRODUCT, HARDWARE_REVISION, IMAGE_TYPE
import sh

from python.bitkey import fw_version
from bitkey.walletfs import WalletFS
from tasks.lib.paths import BUILD_FWUP_BUNDLE_DIR

logging.getLogger("sh").setLevel(logging.WARNING)


class Inv:
    @allure.step("Clean")
    def clean(self):
        result = sh.inv.clean()
        return result

    @allure.step("Build")
    def build(self):
        result = sh.inv.build()
        return result

    @allure.step("Build Platforms")
    def build_platforms(self):
        result = sh.inv("build.platforms")
        return result

    @allure.step("Flash -e")
    def flash(self, target=None):
        if target:
            result = subprocess.check_output(
                "inv flash -e -f -t {0}".format(target), shell=True)
        else:
            result = subprocess.check_output("inv flash -e -f", shell=True)
        print(result)
        return str(result)

    @allure.step("Bundle")
    def fwup_bundle(self, product=PRODUCT, image_type=IMAGE_TYPE, hardware_revision=HARDWARE_REVISION):  # Pass these flags as args eventually
        result = sh.inv("fwup.bundle", "-p", product,
                        "-i", image_type, "-h", hardware_revision)
        return result

    @allure.step("Fwup")
    def fwup_fwup(self):
        result = sh.inv("fwup.fwup", "-f", str(BUILD_FWUP_BUNDLE_DIR))
        return result

    @allure.step("Bump version")
    def bump(self):
        fw_version.bump()

    @allure.step("Set local fw version to: {version}")
    def set_version(self, version):
        fw_version.set(version)

    @allure.step("Backup Filesystem")
    def backup_filesystem(self):
        result = subprocess.check_output("inv fs.backup", shell=True)
        return str(result)

    @allure.step("Restore Filesystem")
    def restore_filesystem(self, file):
        result = sh.inv("fs.restore", "--file=%s" % file)
        return result

    @allure.step("Backup, Flash, and Recover")
    def flash_with_filesystem_recovery(self, target=None):
        result = self.backup_filesystem()
        # re search output string for filename
        fs_backup_file = re.search(
            "saved as (.*).bin", result).group(1) + ".bin"
        result += self.flash(target)
        fs = WalletFS(fs_backup_file)
        fs.remove_file('unlock-secret.bin')
        result += self.restore_filesystem(fs_backup_file)
        return result
