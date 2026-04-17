"""Helper functions for working with J-Link commander."""

import logging
import subprocess

import allure
from tasks.lib.paths import COMMANDER_BIN


class CommanderHelper:
    """Wrapper class for J-Link commander helper functions."""

    @allure.step("Commander device reset")
    def reset(self) -> str:
        """Uses J-Link commander to reset a device.

        :returns: command output.
        :note: this only works with the EFR32.
        """
        result = reset_device()
        print(result)
        return result


def reset_device() -> str:
    """Uses J-Link commander to reset a device.

    :returns: command output.
    :note: this only works with the EFR32.
    """
    result = subprocess.check_output(
        COMMANDER_BIN + " device reset", shell=True, text=True)
    return result
