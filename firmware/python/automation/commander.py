import logging

import allure
from tasks.lib.paths import COMMANDER_BIN
import subprocess


class CommanderHelper:
    @allure.step("Commander device reset")
    def reset(self):
        result = subprocess.check_output(
            COMMANDER_BIN + " device reset", shell=True)
        print(result)
        return result

def reset_device():
    subprocess.check_output(
        COMMANDER_BIN + " device reset", shell=True)
