-- It has been already inserted before so we have to delete that first
DELETE FROM firmwareCoredumpsEntity
WHERE id = 1;

INSERT INTO firmwareCoredumpsEntity (
    id,
    coredump,
    serial,
    swVersion,
    hwVersion,
    swType,
    mcuInfo
) VALUES (
             '1',
             'coredump-val',
             'serial-val',
             'swVersion-val',
             'hwVersion-val',
             'swType-val',
             'CORE:1.0.0/UXC:0.2.0'
         );
