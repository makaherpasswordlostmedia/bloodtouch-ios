import os
import struct
import sys

PT_DYNAMIC = 2
DT_NEEDED = 1
DT_STRTAB = 5
DT_NULL = 0


def read_needed(path):
    with open(path, "rb") as f:
        data = f.read()
    assert data[:4] == b"\x7fELF" and data[4] == 2, "not ELF64: " + path
    e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
    e_phentsize = struct.unpack_from("<H", data, 0x36)[0]
    e_phnum = struct.unpack_from("<H", data, 0x38)[0]

    dyn_off = None
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", data, base)[0]
        if p_type == PT_DYNAMIC:
            dyn_off = struct.unpack_from("<Q", data, base + 0x8)[0]
            break
    if dyn_off is None:
        return []

    entries = []
    off = dyn_off
    while True:
        d_tag, d_val = struct.unpack_from("<QQ", data, off)
        if d_tag == DT_NULL:
            break
        entries.append((d_tag, d_val))
        off += 16

    strtab_va = None
    needed = []
    for tag, val in entries:
        if tag == DT_STRTAB:
            strtab_va = val
        elif tag == DT_NEEDED:
            needed.append(val)

    # map strtab virtual address -> file offset through program headers
    strtab_off = None
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsize
        p_type, p_flags = struct.unpack_from("<II", data, base)
        p_offset, p_vaddr = struct.unpack_from("<QQ", data, base + 0x8)
        p_filesz, p_memsz = struct.unpack_from("<QQ", data, base + 0x20)
        if p_type == 1 and p_flags & 4 and p_vaddr <= strtab_va < p_vaddr + p_filesz:
            strtab_off = p_offset + (strtab_va - p_vaddr)
            break
    if strtab_off is None:
        strtab_off = strtab_va

    out = []
    for idx in needed:
        p = strtab_off + idx
        end = data.index(b"\0", p)
        out.append(data[p:end].decode("ascii"))
    return out


# libraries shipped by Android itself (NDK sysvroot / system linker namespace)
SYSTEM = {
    "libc.so", "libdl.so", "libm.so", "liblog.so", "libandroid.so", "libz.so",
    "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so",
    "libOpenSLES.so", "libOpenMAXAL.so", "libaaudio.so", "libamidenc.so",
    "libmediandk.so", "libcamera2ndk.so", "libnativewindow.so",
    "libvulkan.so", "libneuralnetworks.so", "libsync.so", "libandroid.so",
    "libc++.so", "libc++_shared.so", "libnativehelper.so", "libaudioclient.so",
    "libbinder_ndk.so", "libwebviewandroid_api.so",
}


def main(root):
    have = {n for n in os.listdir(root) if n.endswith(".so")}
    print("shipped: " + ", ".join(sorted(have)))
    print()
    missing = {}
    for name in sorted(have):
        need = read_needed(os.path.join(root, name))
        unresolved = [n for n in need if n not in have and n not in SYSTEM]
        print("%-22s -> %s" % (name, ", ".join(need)))
        for u in unresolved:
            missing.setdefault(u, []).append(name)
    print()
    if missing:
        print("UNRESOLVED:")
        for k, v in sorted(missing.items()):
            print("  %s  (needed by %s)" % (k, ", ".join(v)))
    else:
        print("UNRESOLVED: none")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
