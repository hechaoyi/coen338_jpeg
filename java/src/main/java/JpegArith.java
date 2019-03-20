import arithmetic.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import static java.lang.Math.abs;

class JpegArithEncoder extends Jpeg {
    protected final FrequencyTable dcFreqs;
    protected final FrequencyTable acFreqs;
    protected ArithmeticEncoder arithmeticEncoder;

    public JpegArithEncoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
        this.dcFreqs = new SimpleFrequencyTable(new FlatFrequencyTable(4096)); // -2048~2047
        this.acFreqs = new SimpleFrequencyTable(new FlatFrequencyTable(32768)); // 0~15 | -1024~1023
    }

    @Override
    protected void writeScan(OutputStream os) {
        if (this.arithmeticEncoder == null) {
            this.arithmeticEncoder = new ArithmeticEncoder(32, new BitOutputStream(new OutputStream() {
                @Override
                public void write(int b) {
                    writeWord(os, b, 1);
                    if (b == 0xff)
                        writeWord(os, 0x00, 1);
                }
            }));
        }
        super.writeScan(os);
        try {
            this.arithmeticEncoder.finish();
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
            int sym;
            if (maxCategory == 11) { // DC
                sym = symbol + 2048;
                this.arithmeticEncoder.write(this.dcFreqs, sym);
                this.dcFreqs.increment(sym);
            } else { // AC
                sym = (zeros << 11) | abs(symbol);
                if (symbol < 0)
                    sym |= 0x400;
                this.arithmeticEncoder.write(this.acFreqs, sym);
                this.acFreqs.increment(sym);
            }
            this.symbolFreqStats.computeIfAbsent(huffman, h -> new HashMap<>())
                    .compute(sym, (s, c) -> c != null ? c + 1 : 1);
            return 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

class JpegArithDecoder extends Jpeg {
    protected final FrequencyTable dcFreqs;
    protected final FrequencyTable acFreqs;
    protected ArithmeticDecoder arithmeticDecoder;

    public JpegArithDecoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
        this.dcFreqs = new SimpleFrequencyTable(new FlatFrequencyTable(4096)); // -2048~2047
        this.acFreqs = new SimpleFrequencyTable(new FlatFrequencyTable(32768)); // 0~15 | -1024~1023
    }

    @Override
    protected void readScan() {
        if (this.arithmeticDecoder == null) {
            try {
                this.arithmeticDecoder = new ArithmeticDecoder(32, new BitInputStream(new InputStream() {
                    @Override
                    public int read() {
                        return nextByteInScan();
                    }
                }));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        super.readScan();
    }

    protected int readDcValue(Huffman huffman) {
        try {
            int sym = this.arithmeticDecoder.read(this.dcFreqs);
            this.dcFreqs.increment(sym);
            return sym - 2048;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected int readAcValue(Huffman huffman, int[] zeroHolder) {
        try {
            int sym = this.arithmeticDecoder.read(this.acFreqs);
            this.acFreqs.increment(sym);
            zeroHolder[0] = (sym >> 11) & 0xf;
            return ((sym & 0x400) != 0) ? -(sym & 0x3ff) : (sym & 0x3ff);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

class JpegArith {
    public static void main(String[] args) throws IOException {
        String file = "images/VEll6n1SaRHUyAiMHpg7tA.jpeg";
        String jpp = file.replaceAll("[.].+?$", ".jpp");
        var jae = new JpegArithEncoder(file, jpp);
        jae.recompress();
        var jad = new JpegArithDecoder(jpp, file.replaceAll("[.].+?$", ".out.jpg"));
        jad.recompress();
    }
}