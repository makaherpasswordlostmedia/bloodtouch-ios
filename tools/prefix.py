import sys

a = open(sys.argv[1], "rb").read()
b = open(sys.argv[2], "rb").read()
n = min(len(a), len(b))
i = 0
while i < n and a[i] == b[i]:
    i += 1
print("smaller=%d larger=%d first difference at offset %d" % (len(a), len(b), i))
if i == n:
    print("PREFIX: the shorter file is a strict prefix of the longer one")
else:
    print("ref[%d:%d] = %r" % (max(0, i - 8), i + 16, a[max(0, i - 8):i + 16]))
    print("new[%d:%d] = %r" % (max(0, i - 8), i + 16, b[max(0, i - 8):i + 16]))
    print("bytes differing:", sum(1 for x, y in zip(a, b) if x != y))
