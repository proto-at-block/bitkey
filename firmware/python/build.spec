# -*- mode: python ; coding: utf-8 -*-
"""
Based on https://github.com/chriskiehl/Gooey/blob/master/docs/packaging/Packaging-Gooey.md
"""


import os
import platform
import gooey
gooey_root = os.path.dirname(gooey.__file__)
gooey_languages = Tree(os.path.join(gooey_root, 'languages'), prefix = 'gooey/languages')
gooey_images = Tree(os.path.join(gooey_root, 'images'), prefix = 'gooey/images')

from PyInstaller.building.api import EXE, PYZ, COLLECT
from PyInstaller.building.build_main import Analysis
from PyInstaller.building.datastruct import Tree
from PyInstaller.building.osx import BUNDLE

import sys
sys.modules['FixTk'] = None

block_cipher = None

a = Analysis(['./bitkey/flasher_gui.py'],
             pathex=['./bitkey/flasher_gui.py', './wallet'],
             hiddenimports=[],
             hookspath=None,
             runtime_hooks=None,
             excludes=['FixTk', 'tcl', 'tk', '_tkinter', 'tkinter', 'Tkinter'],
             )
pyz = PYZ(a.pure)

options = [('u', None, 'OPTION'), ('w', None, 'OPTION')]


exe = EXE(pyz,
          a.scripts,
          a.binaries,
          a.zipfiles,
          a.datas,
          options,
          gooey_languages,
          gooey_images,
          name='W1 Flashing Tool',
          debug=False,
          strip=False,
          upx=True,
          console=False,
          icon=os.path.join(gooey_root, 'images', 'program_icon.ico'))

info_plist = {'addition_prop': 'additional_value'}
app = BUNDLE(exe,
             name='W1Flasher.app',
             bundle_identifier=None,
             info_plist=info_plist
            )
