import collections

import numpy as np
from PIL import Image
from scipy import fftpack

from jpeg_reader import JpegReader


class JpegWriter:
    def __init__(self, reader):
        self.reader = reader
        self.components = None

    def write(self, file):
        self.deconstruct_image()
        with open(file, 'wb') as f:
            self._write_metadata(f)
            self._write_scan(f)
            f.write(0xffd9.to_bytes(2, 'big'))  # EOI

    def deconstruct_image(self):
        # npmat = self.reader.npmat
        npmat = np.array(Image.open('Lenna.jpg').convert('YCbCr'))  # TODO
        # filter = np.array([[1 / 9, 1 / 9, 1 / 9], [1 / 9, 1 / 9, 1 / 9], [1 / 9, 1 / 9, 1 / 9]])
        # npmat[:, :, 1] = convolve(npmat[:, :, 1], filter)
        # npmat[:, :, 2] = convolve(npmat[:, :, 2], filter)
        components = [[], [], []]
        for i in range(0, self.reader.meta['rows'], 16):
            for j in range(0, self.reader.meta['cols'], 16):
                # 4:2:0
                # Y
                components[0] += [
                    (dct(npmat[i:i + 8, j:j + 8, 0] - 128) // self.reader.meta['QT0']).astype(np.int32),
                    (dct(npmat[i:i + 8, j + 8:j + 16, 0] - 128) // self.reader.meta['QT0']).astype(np.int32),
                    (dct(npmat[i + 8:i + 16, j:j + 8, 0] - 128) // self.reader.meta['QT0']).astype(np.int32),
                    (dct(npmat[i + 8:i + 16, j + 8:j + 16, 0] - 128) // self.reader.meta['QT0']).astype(np.int32)
                ]
                # Cb
                components[1] += [
                    (dct(npmat[i:i + 16:2, j:j + 16:2, 1] - 128) // self.reader.meta['QT1']).astype(np.int32)
                ]
                # Cr
                components[2] += [
                    (dct(npmat[i:i + 16:2, j:j + 16:2, 2] - 128) // self.reader.meta['QT1']).astype(np.int32)
                ]
        for component in components:
            for i in range(len(component) - 1, 0, -1):
                component[i][0, 0] -= component[i - 1][0, 0]  # DC prediction
        self.components = components

    def _write_metadata(self, file):
        metadata = self.reader.src[:self.reader.scan_start_pos]
        file.write(metadata)

    def _write_scan(self, file):
        buffer, offset = bytearray(), 0
        for i in range(3):
            self.components[i] = collections.deque(self.components[i])
        blocks, j = [(0, self.components[0]), (0, self.components[0]),
                     (0, self.components[0]), (0, self.components[0]),
                     (1, self.components[1]), (1, self.components[2])], 0
        while blocks[j % len(blocks)][1]:
            block = blocks[j % len(blocks)][1].popleft()
            self._write_block(blocks[j % len(blocks)][0], block, buffer, offset)
            j += 1
        # byte stuffing
        if offset != 0:
            buffer[-1] &= 2 ** (8 - offset) - 1
        file.write(buffer.replace(b'\xff', b'\xff\x00'))

    def _write_block(self, t, block, buffer, offset):
        zigzag = block_to_zigzag(block)
        offset = self.dc(t, zigzag[0], buffer, offset)
        last = 0
        for i in range(1, len(zigzag)):
            if zigzag[i] == 0 and i - last < 16:
                continue
            offset = self.ac(t, i - last - 1, zigzag[i], buffer, offset)
            last = i
        offset = self.ac(t, 0, 0, buffer, offset)  # EOB
        return offset

    def dc(self, t, symbol, buffer, offset):
        if symbol == 0:
            return binary(self.reader.meta[f'HT{t}-dc'][0], buffer, offset)
        abs_symbol = abs(symbol)
        for i in range(1, 12):
            if abs_symbol < 2 ** i:
                break
        if i not in self.reader.meta[f'HT{t}-dc']:  # TODO
            print(f'not in HT{t}-dc huffman dict {i} {symbol}')
            return binary('1111011111', buffer, offset)
        prefix = self.reader.meta[f'HT{t}-dc'][i]
        return binary(f'{prefix}{symbol if symbol > 0 else symbol + 2 ** i - 1:b}', buffer, offset)

    def ac(self, t, zeros, symbol, buffer, offset):
        if zeros == 0 and symbol == 0:
            return binary(self.reader.meta[f'HT{t}-ac'][0], buffer, offset)
        abs_symbol = abs(symbol)
        for i in range(1, 11):
            if abs_symbol < 2 ** i:
                break
        if zeros * 16 + i not in self.reader.meta[f'HT{t}-ac']:  # TODO
            print(f'not in HT{t}-ac huffman dict {zeros * 16 + i} {symbol}')
            return binary('010', buffer, offset)
        prefix = self.reader.meta[f'HT{t}-ac'][zeros * 16 + i]
        return binary(f'{prefix}{symbol if symbol > 0 else symbol + 2 ** i - 1:b}', buffer, offset)


def binary(s, buffer, offset):
    if offset != 0:
        i = 8 - offset
        part, s = s[:i], s[i:]
        buffer[-1] &= int(part, 2) << (i - len(part))
        offset = (offset + len(part)) % 8
        if not s:
            return offset
    assert offset == 0
    if len(s) % 8 == 0:
        buffer.extend(int(s, 2).to_bytes(len(s) // 8, 'big'))
        return 0
    else:
        buffer.extend((int(s, 2) << (8 - len(s) % 8)).to_bytes(len(s) // 8 + 1, 'big'))
        return len(s) % 8


def block_to_zigzag(block):
    zigzag = []
    for i in range(8):
        for j in (range(i + 1) if i & 1 == 0 else range(i, -1, -1)):
            zigzag.append(block[i - j, j])
    for i in range(1, 8):
        for j in (range(i, 8) if i & 1 == 1 else range(7, i - 1, -1)):
            zigzag.append(block[i + 7 - j, j])
    return zigzag


def dct(image):
    return fftpack.dct(fftpack.dct(image.T, norm='ortho').T, norm='ortho')


if __name__ == '__main__':
    r = JpegReader('Lenna.jpg')
    r.read()
    JpegWriter(r).write('Lenna.out')
    # head -c 372 Lenna.jpg | md5
    # 096a828af51b827e77fc20b82ba665af
    # cat Lenna.out | md5
    # 096a828af51b827e77fc20b82ba665af
