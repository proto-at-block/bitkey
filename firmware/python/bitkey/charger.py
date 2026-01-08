import enum


class MAX77734Regs(enum.IntEnum):
    INT_GLBL = 0x00
    INT_CHG = 0x01
    STAT_CHG_A = 0x02
    STAT_CHG_B = 0x03
    ERCFLAG = 0x04
    STAT_GLBL = 0x05
    INTM_GLBL = 0x06
    INT_M_CHG = 0x07
    CNFG_GLBL = 0x08
    CID = 0x09
    CNFG_WDT = 0x0A
    CNFG_CHG_A = 0x20
    CNFG_CHG_B = 0x21
    CNFG_CHG_C = 0x22
    CNFG_CHG_D = 0x23
    CNFG_CHG_E = 0x24
    CNFG_CHG_F = 0x25
    CNFG_CHG_G = 0x26
    CNFG_CHG_H = 0x27
    CNFG_CHG_I = 0x28
    CNFG_LDO_A = 0x30
    CNFG_LDO_B = 0x31
    CNFG_SNK1_A = 0x40
    CNFG_SNK1_B = 0x41
    CNFG_SNK2_A = 0x42
    CNFG_SNK2_B = 0x43
    CNFG_SNK_TOP = 0x44
