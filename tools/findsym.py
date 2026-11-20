import sys, struct
from capstone import *

path, want = sys.argv[1], sys.argv[2]
d = open(path, 'rb').read()
shoff = struct.unpack_from('<Q', d, 0x28)[0]
shentsize, shnum, shstrndx = struct.unpack_from('<HHH', d, 0x3A)
secs = []
for i in range(shnum):
    o = shoff + i * shentsize
    secs.append(struct.unpack_from('<IIQQQQIIQQ', d, o))
shstr = secs[shstrndx]


def sname(n):
    b = shstr[4] + n
    return d[b:d.index(b'\0', b)].decode(errors='replace')


text = [s for s in secs if sname(s[0]) == '.text'][0]
md = Cs(CS_ARCH_ARM64, CS_MODE_ARM + CS_MODE_LITTLE_ENDIAN)

symname = '.dynsym' if len(sys.argv) < 4 else sys.argv[3]
for sec in secs:
    if sname(sec[0]) != symname:
        continue
    strsec = secs[sec[6]]
    for j in range(sec[5] // sec[9]):
        so = sec[4] + j * sec[9]
        st_name, st_info, st_other, st_shndx, st_value, st_size = struct.unpack_from('<IBBHQQ', d, so)
        b = strsec[4] + st_name
        nm = d[b:d.index(b'\0', b)].decode(errors='replace')
        if want in nm and st_value:
            print('SYM', nm, hex(st_value), st_size)
            foff = text[4] + (st_value - text[3])
            for ins in list(md.disasm(d[foff:foff + (st_size or 160)], st_value))[:45]:
                print(hex(ins.address), ins.mnemonic, ins.op_str)
