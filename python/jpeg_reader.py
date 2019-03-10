import numpy as np
from PIL import Image
from scipy import fftpack


class JpegReader:
    def __init__(self, file):
        with open(file, 'rb') as f:
            self.src = f.read()
        self.pos = 0
        self.meta = {}
        self.scan_start_pos = None
        self.raw = None
        self.npmat = None

    def read(self):
        # https://en.wikipedia.org/wiki/JPEG#Syntax_and_structure
        # Spec: https://www.w3.org/Graphics/JPEG/itu-t81.pdf
        assert self.word() == 0xffd8, 'SOI'
        self._skip_markers()
        self._read_quantization()
        self._read_frame_marker()
        self._read_huffman()
        self._read_scan_marker()
        self._read_scan()
        assert self.word() == 0xffd9, 'EOI'
        self.construct_image()
        print(self.meta)

    def construct_image(self):
        for raw in self.raw:
            for i in range(1, len(raw)):
                raw[i][0, 0] += raw[i - 1][0, 0]  # DC prediction
        npmat = np.empty((self.meta['rows'], self.meta['cols'], 3), dtype=np.uint8)
        n = 0
        for i in range(0, self.meta['rows'], 16):
            for j in range(0, self.meta['cols'], 16):
                # 4:2:0
                # Y
                npmat[i:i + 8, j:j + 8, 0] = idct(self.raw[0][n] * self.meta['QT0']) + 128
                npmat[i:i + 8, j + 8:j + 16, 0] = idct(self.raw[0][n + 1] * self.meta['QT0']) + 128
                npmat[i + 8:i + 16, j:j + 8, 0] = idct(self.raw[0][n + 2] * self.meta['QT0']) + 128
                npmat[i + 8:i + 16, j + 8:j + 16, 0] = idct(self.raw[0][n + 3] * self.meta['QT0']) + 128
                # Cb
                npmat[i:i + 16, j:j + 16, 1] = (idct(self.raw[1][n // 4] * self.meta['QT1']) + 128) \
                    .repeat(2, axis=0).repeat(2, axis=1)
                # Cr
                npmat[i:i + 16, j:j + 16, 2] = (idct(self.raw[2][n // 4] * self.meta['QT1']) + 128) \
                    .repeat(2, axis=0).repeat(2, axis=1)
                n += 4
        self.npmat = npmat

    def _skip_markers(self):
        while self.word() & 0xfff0 == 0xffe0:
            length = self.word()
            self.pos += length - 2
        self.pos -= 2

    def _read_quantization(self):
        while self.word() == 0xffdb:  # DQT
            length = self.word()
            key = f'QT{self.src[self.pos] & 0x0f}'
            value = np.frombuffer(self.src[self.pos + 1: self.pos + length - 2], dtype=np.uint8)
            self.meta[key] = zigzag_to_block(value)
            self.pos += length - 2
        self.pos -= 2

    def _read_frame_marker(self):
        assert self.word() == 0xffc0, 'SOF0'
        length = self.word()
        limit = self.pos + length - 2
        self.pos += 1
        self.meta['rows'] = self.word()
        self.meta['cols'] = self.word()
        self.meta['components'] = n = self.word(1)
        self.pos += n * 3
        assert self.pos == limit, f'{self.pos} {limit}'

    def _read_huffman(self):
        while self.word() == 0xffc4:  # DHT
            length = self.word()
            key = f'HT{self.src[self.pos] & 0x0f}-{"dc" if self.src[self.pos] & 0x10 == 0 else "ac"}'
            nums = self.src[self.pos + 1: self.pos + 17]
            symbols, j = self.src[self.pos + 17: self.pos + length - 2], 0
            mapping, n = {}, 0
            for i in range(0, 16):
                n = n << 1
                for _ in range(nums[i]):
                    mapping[symbols[j]] = f'{n:0{i + 1}b}'
                    j, n = j + 1, n + 1
            assert j == len(symbols)
            self.meta[key] = mapping
            self.meta[f'{key}-dex'] = {mapping[s]: s for s in mapping}
            self.meta[f'{key}-max'] = max(len(s) for s in mapping.values())
            self.pos += length - 2
        self.pos -= 2

    def _read_scan_marker(self):
        assert self.word() == 0xffda, 'SOS'
        length = self.word()
        limit = self.pos + length - 2
        n = self.word(1)
        self.pos += n * 2
        self.pos += 3
        assert self.pos == limit, f'{self.pos} {limit}'
        self.scan_start_pos = self.pos

    def _read_scan(self):
        for i in range(self.pos, len(self.src) - 1):
            if self.src[i] == 0xff and self.src[i + 1] != 0x00:
                break
        length = i - self.pos
        escaped, start, offset = self.src[self.pos:i].replace(b'\xff\x00', b'\xff'), 0, 0
        components = [], [], []
        blocks, j = [(0, components[0]), (0, components[0]), (0, components[0]), (0, components[0]),
                     (1, components[1]), (1, components[2])], 0
        while start < len(escaped):
            if start == len(escaped) - 1 and all(c == '1' for c in binary(escaped[-1:])[offset:]):
                break
            block, start, offset = self._read_block(blocks[j % len(blocks)][0], escaped, start, offset)
            blocks[j % len(blocks)][1].append(block)
            j += 1
        self.raw = components
        self.pos += length

    def _read_block(self, t, source, start, offset):
        symbol, start, offset = self.dc(t, source, start, offset)
        block = [symbol]
        while start < len(source):
            zeros, symbol, start, offset = self.ac(t, source, start, offset)
            if zeros == 0 and symbol == 0:  # EOB
                block.extend([0] * (64 - len(block)))
                break
            if zeros:
                block.extend([0] * zeros)
            block.append(symbol)
        return zigzag_to_block(block), start, offset

    def word(self, length=2):
        i, self.pos = self.pos, self.pos + length
        return int.from_bytes(self.src[i:i + length], 'big')

    def dc(self, t, source, start, offset):
        c, i = huffman(source, self.meta[f'HT{t}-dc-dex'], self.meta[f'HT{t}-dc-max'], start, offset)
        start, offset = start + (offset + i) // 8, (offset + i) % 8
        if c == 0:
            return 0, start, offset
        s = int(binary(source[start:start + 3])[offset:offset + c], 2)
        return s if s & (1 << (c - 1)) else (1 - 2 ** c + s), start + (offset + c) // 8, (offset + c) % 8

    def ac(self, t, source, start, offset):
        c, i = huffman(source, self.meta[f'HT{t}-ac-dex'], self.meta[f'HT{t}-ac-max'], start, offset)
        z, c = c // 16, c % 16
        start, offset = start + (offset + i) // 8, (offset + i) % 8
        if c == 0:
            return z, 0, start, offset
        s = int(binary(source[start:start + 3])[offset:offset + c], 2)
        return z, s if s & (1 << (c - 1)) else (1 - 2 ** c + s), start + (offset + c) // 8, (offset + c) % 8


def huffman(source, table, max_len, start, offset):
    src = binary(source[start:start + 3])[offset:]
    for i in range(1, max(max_len, len(src)) + 1):
        if src[:i] in table:
            return table[src[:i]], i
    assert 0, f'unknown huffman {start}-{offset} for {table} - {source[start:start + 16]}'


def binary(bytes):
    return f'{int.from_bytes(bytes, "big"):0{len(bytes) * 8}b}'


def zigzag_to_block(zigzag):
    block = np.empty((8, 8), np.int32)
    n = 0
    for i in range(8):
        for j in (range(i + 1) if i & 1 == 0 else range(i, -1, -1)):
            block[i - j, j] = zigzag[n]
            n += 1
    for i in range(1, 8):
        for j in (range(i, 8) if i & 1 == 1 else range(7, i - 1, -1)):
            block[i + 7 - j, j] = zigzag[n]
            n += 1
    return block


def idct(image):
    return fftpack.idct(fftpack.idct(image.T, norm='ortho').T, norm='ortho')


if __name__ == '__main__':
    r = JpegReader('Lenna.jpg')
    r.read()
    Image.fromarray(r.npmat, 'YCbCr').show()
