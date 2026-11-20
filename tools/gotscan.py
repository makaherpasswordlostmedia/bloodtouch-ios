import struct, sys
from capstone import *
path = sys.argv[1]
d = open(path,'rb').read()
shoff=struct.unpack_from('<Q',d,0x28)[0]
shentsize,shnum,shstrndx=struct.unpack_from('<HHH',d,0x3A)
S=[struct.unpack_from('<IIQQQQIIQQ',d,shoff+i*shentsize) for i in range(shnum)]
shstr=S[shstrndx]
def nm(n):
    b=shstr[4]+n; return d[b:d.index(b'\0',b)].decode(errors='replace')
byname={nm(s[0]):s for s in S}
dyn=byname['.dynsym']; dst=byname['.dynstr']
def sname(off):
    b=dst[4]+off; return d[b:d.index(b'\0',b)].decode(errors='replace')
rela = byname.get('.rela.dyn')
targets={}
want=('GLScaleWidth','GLScaleHeight')
n=rela[5]//rela[9]
for j in range(n):
    o=rela[4]+j*rela[9]
    r_off,r_info,r_add=struct.unpack_from('<QQQ',d,o)
    sym=r_info>>32
    so=dyn[4]+sym*dyn[9]
    st_name=struct.unpack_from('<I',d,so)[0]
    s=sname(st_name)
    if any(w in s for w in want):
        print('RELOC',hex(r_off),s,sname(r_info&0xffffffff) if False else '', hex(r_add))
        targets[r_off]=s
