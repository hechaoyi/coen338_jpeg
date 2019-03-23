import arithmetic.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.NoSuchElementException;

import static java.lang.Math.abs;

class JpegArithEncoder extends Jpeg {
    protected final FrequencyTable freqs;
    protected ArithmeticEncoder arithmeticEncoder;

    public JpegArithEncoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
        this.freqs = new SimpleFrequencyTable(new FlatFrequencyTable(32769));
    }

    @Override
    protected void writeScan(OutputStream os) {
        BitOutputStream output = new BitOutputStream(new OutputStream() {
            @Override
            public void write(int b) {
                writeWord(os, b, 1);
                if (b == 0xff)
                    writeWord(os, 0x00, 1);
            }
        });
        this.arithmeticEncoder = new ArithmeticEncoder(32, output);
        super.writeScan(os);
        try {
            this.arithmeticEncoder.write(freqs, 32768);  // EOF
            this.arithmeticEncoder.finish();
            output.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void writeByteInScan(OutputStream os, int value, int bits) {
    }

    @Override
    protected int encodeValueInRunningCategory(int zeros, int symbol, int maxCategory, Huffman huffman, int[] bitsHolder) {
        try {
            int sym = (zeros << (maxCategory + 1)) | (abs(symbol) & this.mask(maxCategory));
            if (symbol < 0)
                sym |= (1 << maxCategory);
            this.arithmeticEncoder.write(this.freqs, sym);
            this.freqs.increment(sym);
            this.symbolFreqStats.computeIfAbsent(huffman, h -> new HashMap<>())
                    .compute(sym, (s, c) -> c != null ? c + 1 : 1);
            return 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

class JpegArithDecoder extends Jpeg {
    protected final FrequencyTable freqs;
    protected ArithmeticDecoder arithmeticDecoder;

    public JpegArithDecoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
        this.freqs = new SimpleFrequencyTable(new FlatFrequencyTable(32769));
    }

    @Override
    protected void readScan() {
        try {
            this.arithmeticDecoder = new ArithmeticDecoder(32, new BitInputStream(new InputStream() {
                @Override
                public int read() {
                    int b = readWord(1, 2);
                    if (b != 0xff)
                        return b;
                    int bb = readWord(1);
                    if (bb == 0x00)
                        return b;
                    rewind(2);
                    return -1;
                }
            }));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            super.readScan();
        } catch (NoSuchElementException e) {
            return;
        }
    }

    @Override
    protected int nextByteInScan() {
        return 0;
    }

    protected int readDcValue(Huffman huffman) {
        try {
            int sym = this.arithmeticDecoder.read(this.freqs);
            if (sym == 32768) // EOF
                throw new NoSuchElementException();
            this.freqs.increment(sym);
            return ((sym & 0x800) != 0) ? -(sym & 0x7ff) : (sym & 0x7ff);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected int readAcValue(Huffman huffman, int[] zeroHolder) {
        try {
            int sym = this.arithmeticDecoder.read(this.freqs);
            if (sym == 32768) // EOF
                throw new NoSuchElementException();
            this.freqs.increment(sym);
            zeroHolder[0] = (sym >> 11) & 0xf;
            return ((sym & 0x400) != 0) ? -(sym & 0x3ff) : (sym & 0x3ff);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

class JpegArith {
    public static void main(String[] args) throws IOException {
        String file = "images/VEll6n1SaRHUyAiMHpg7tA.jpg";
        String jpp = file.replaceAll("[.].+?$", ".jpp");
        new JpegArithEncoder(file, jpp).recompress();
        new JpegArithDecoder(jpp, file.replaceAll("[.].+?$", ".out.jpg")).recompress();
    }
}