import struct, sys
from capstone import *

path = sys.argv[1]
tgts = [int(x, 16) for x in sys.argv[2].split(',')]
d = open(path, 'rb').read()
shoff = struct.unpack_from('<Q', d, 0x28)[0]
she, shn, shx = struct.unpack_from('<HHH', d, 0x3A)
S = [struct.unpack_from('<IIQQQQIIQQ', d, shoff + i * she) for i in range(shn)]
shstr = S[shx]


def nm(n):
    b = shstr[4] + n
    return d[b:d.index(b'\0', b)].decode(errors='replace')


text = [s for s in S if nm(s[0]) == '.text'][0]
md = Cs(CS_ARCH_ARM64, CS_MODE_ARM + CS_MODE_LITTLE_ENDIAN)
ins = list(md.disasm(d[text[4]:text[4] + text[5]], text[3]))
print('decoded', len(ins))
page = {}
hits = []
for k, i in enumerate(ins):
    if i.mnemonic == 'adrp':
        p = i.op_str.split('#')
        page[p[0].split(',')[0].strip()] = int(p[1], 16)
    elif i.mnemonic in ('add', 'ldr', 'str') and ',' in i.op_str:
        s = i.op_str.replace('[', ', ').replace(']', '')
        parts = [x.strip() for x in s.split(',')]
        if i.mnemonic == 'add':
            parts = [x.strip() for x in i.op_str.split(',')]
        if len(parts) >= 2 and parts[-1].startswith('#'):
            base = parts[-2] if i.mnemonic != 'add' else parts[1]
            if base in page:
                addr = page[base] + int(parts[-1][1:], 16)
                if addr in tgts:
                    hits.append(k)
for k in hits:
    print('==== hit at', hex(ins[k].address))
    for i in ins[max(0, k - 8):k + 12]:
        print(' ', hex(i.address), i.mnemonic, i.op_str)
