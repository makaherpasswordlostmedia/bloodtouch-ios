import struct, sys

def elf(path):
    d = open(path, 'rb').read()
    assert d[:4] == b'\x7fELF'
    ei_class = d[4]
    little = d[5] == 1
    e = '<' if little else '>'
    if ei_class == 2:
        e_shoff, = struct.unpack_from(e+'Q', d, 0x28)
        e_shentsize, = struct.unpack_from(e+'H', d, 0x3a)
        e_shnum, = struct.unpack_from(e+'H', d, 0x3c)
        e_phoff, = struct.unpack_from(e+'Q', d, 0x20)
        e_phentsize, = struct.unpack_from(e+'H', d, 0x36)
        e_phnum, = struct.unpack_from(e+'H', d, 0x38)
    else:
        e_shoff, = struct.unpack_from(e+'I', d, 0x18)
        e_shentsize, = struct.unpack_from(e+'H', d, 0x2e)
        e_shnum, = struct.unpack_from(e+'H', d, 0x30)
        e_phoff, = struct.unpack_from(e+'I', d, 0x1c)
        e_phentsize, = struct.unpack_from(e+'H', d, 0x2a)
        e_phnum, = struct.unpack_from(e+'H', d, 0x2c)

    sections = {}
    for i in range(e_shnum):
        off = e_shoff + i*e_shentsize
        if ei_class == 2:
            sh_type, = struct.unpack_from(e+'I', d, off+4)
            sh_offset, = struct.unpack_from(e+'Q', d, off+0x18)
            sh_size, = struct.unpack_from(e+'Q', d, off+0x20)
            sh_link, = struct.unpack_from(e+'I', d, off+0x28)
        else:
            sh_type, = struct.unpack_from(e+'I', d, off+4)
            sh_offset, = struct.unpack_from(e+'I', d, off+0x10)
            sh_size, = struct.unpack_from(e+'I', d, off+0x14)
            sh_link, = struct.unpack_from(e+'I', d, off+0x18)
        sections[i] = (sh_type, sh_offset, sh_size, sh_link)

    def cstr(tab, x):
        s = d.index(b'\0', tab+x)
        return d[tab+x:s].decode('latin1')

    out = {'needed': [], 'soname': None, 'undef': [], 'defined': []}
    # dynamic via program headers (PT_DYNAMIC=2)
    dyn_off = dyn_sz = None
    for i in range(e_phnum):
        off = e_phoff + i*e_phentsize
        p_type, = struct.unpack_from(e+'I', d, off)
        if p_type == 2:
            if ei_class == 2:
                p_offset, = struct.unpack_from(e+'Q', d, off+8)
                p_filesz, = struct.unpack_from(e+'Q', d, off+32)
            else:
                p_offset, = struct.unpack_from(e+'I', d, off+4)
                p_filesz, = struct.unpack_from(e+'I', d, off+16)
            dyn_off, dyn_sz = p_offset, p_filesz
    ent = 16 if ei_class == 2 else 8
    strtab = 0
    symtab = 0
    ents = []
    for i in range(dyn_sz // ent):
        off = dyn_off + i*ent
        if ei_class == 2:
            tag, val = struct.unpack_from(e+'QQ', d, off)
        else:
            tag, val = struct.unpack_from(e+'II', d, off)
        ents.append((tag, val))
        if tag == 5:
            strtab = val
        elif tag == 0:
            break
    for tag, val in ents:
        if tag == 1:
            out['needed'].append(cstr(strtab, val))
        elif tag == 14:
            out['soname'] = cstr(strtab, val)
    # symbols from SHT_SYMTAB/DYNSYM sections
    for i, (sh_type, sh_offset, sh_size, sh_link) in sections.items():
        if sh_type in (2, 11):
            strtab2 = sections[sh_link][1]
            syment = 24 if ei_class == 2 else 16
            for j in range(sh_size // syment):
                o = sh_offset + j*syment
                st_name, st_info = struct.unpack_from(e+'IB', d, o)
                if st_name == 0:
                    continue
                name = cstr(strtab2, st_name)
                (out['defined'] if st_info & 0xf != 0 else out['undef']).append(name)
    return out

if __name__ == '__main__':
    for p in sys.argv[1:]:
        r = elf(p)
        print('== %s' % p)
        print('   soname: %s' % r['soname'])
        print('   needed: %s' % ', '.join(r['needed']))
