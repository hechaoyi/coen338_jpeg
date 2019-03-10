import numpy as np
from PIL import Image
from scipy import fftpack


def dct_2d(image):
    return fftpack.dct(fftpack.dct(image.T, norm='ortho').T, norm='ortho')


image = Image.open('Lenna.jpg')
ycbcr = image.convert('YCbCr')
npmat = np.array(ycbcr, dtype=np.uint8)
print(npmat[150:160, 150:160, 0])
#
# i, j = 0, 0
# block = npmat[i:i + 8, j:j + 8, 0] - 128
# dct_matrix = dct_2d(block)
# q = np.array([[12, 8, 8, 8, 9, 8, 12, 9],
#               [9, 12, 17, 11, 10, 11, 17, 21],
#               [15, 12, 12, 15, 21, 24, 19, 19],
#               [21, 19, 19, 24, 23, 18, 20, 20],
#               [20, 20, 18, 23, 23, 27, 28, 30],
#               [28, 27, 23, 36, 36, 39, 39, 36],
#               [36, 53, 51, 51, 51, 53, 59, 59],
#               [59, 59, 59, 59, 59, 59, 59, 59]], dtype=np.uint8)
# print((dct_matrix // q).round().astype(np.int32))
