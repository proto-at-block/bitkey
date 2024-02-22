# Upgrading ST-RFAL

RFAL is released here: https://www.st.com/en/embedded-software/stsw-st25rfal002.html

To upgrade, download the latest from ST's site and replace the sources in this directory.
The release notes include details on how to port your code in case it isn't backwards compatible.

**IMPORTANT: We've made a few changes to the ST-RFAL sources directly.**

Search for them with the pattern `//!!BLOCK` and be sure to port those over after you upgrade.
