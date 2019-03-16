import java.io.*;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.abs;
import static java.lang.Math.pow;

public class Jpeg {
    private final String inputFileName;
    private final String outputFileName;
    private InputStream is;
    private int bytesRead = 0;
    private int bytesWritten = 0;
    protected int[] quantizationTable0;
    protected int[] quantizationTable1;
    private Huffman dc0;
    private Huffman dc1;
    private Huffman ac0;
    private Huffman ac1;
    private int scan_current;
    private int scan_offset;
    protected List<int[]> componentY = new ArrayList<>();
    protected List<int[]> componentCb = new ArrayList<>();
    protected List<int[]> componentCr = new ArrayList<>();
    private Map<Huffman, Map<Integer, Integer>> dcFreqStats = new HashMap<>();

    public Jpeg(String inputFileName, String outputFileName) {
        this.inputFileName = inputFileName;
        this.outputFileName = outputFileName;
    }

    public void recompress() throws IOException {
        try (var is = new BufferedInputStream(new FileInputStream(this.inputFileName));
             var os = new BufferedOutputStream(new FileOutputStream(this.outputFileName))) {
            this.is = is;

            // SOI
            checkState(this.readWord(2) == 0xffd8, "SOI not detected");
            this.writeWord(os, 0xffd8, 2);

            this.skipApplicationSpecificMarkers(os);
            this.readQuantizationTables(os);
            this.readFrameMarker(os);
            this.readHuffmanTable(os);
            this.readScanMarker(os);
            this.readScan();
            this.depredictAndDequantize();
            this.quantizeAndPredict();
            this.writeScan(os);

            // EOI
            checkState(this.readWord(2) == 0xffd9, "EOI not detected");
            this.writeWord(os, 0xffd9, 2);
            System.out.printf("%d bytes read, %d bytes written\n", this.bytesRead, this.bytesWritten);
        }
    }

    /*
     * --------------------
     * JPEG Syntax
     * https://en.wikipedia.org/wiki/JPEG#Syntax_and_structure
     * Spec: https://www.w3.org/Graphics/JPEG/itu-t81.pdf
     * --------------------
     */

    private void skipApplicationSpecificMarkers(OutputStream os) {
        // TIFF, EXIF, etc.
        int marker;
        while (((marker = this.readWord(2, 2)) & 0xfff0) == 0xffe0) {  // APPn
            int length = this.readWord(2);
            this.writeWord(os, marker, 2);
            this.writeWord(os, length, 2);
            this.copy(os, length - 2);
            System.out.printf("APP marker %x found [%d,%d]\n", marker & 0x1f, length, this.bytesRead);
        }
        this.rewind(2);
    }

    private void readQuantizationTables(OutputStream os) {
        while (this.readWord(2, 2) == 0xffdb) {  // DQT
            int length = this.readWord(2);
            int id = this.readWord(1);
            byte[] bytes = this.read(length - 3);
            checkState(bytes.length == 64, "Unrecognized Quantization Table");
            int[] table = new int[64];
            for (int i = 0; i < 64; i++)
                table[i] = bytes[i] & 0xff;
            if ((id & 0x01) == 0)
                this.quantizationTable0 = table;
            else
                this.quantizationTable1 = table;
            this.writeWord(os, 0xffdb, 2);
            this.writeWord(os, length, 2);
            this.writeWord(os, id, 1);
            this.write(os, bytes);
            System.out.printf("Quantization table %x found [%d,%d]\n", id, length, this.bytesRead);
            // System.out.println(Arrays.toString(table));
        }
        this.rewind(2);
    }

    private void readFrameMarker(OutputStream os) {
        // SOF0, pdf P35 B.2.2
        checkState(this.readWord(2) == 0xffc0, "SOF0 not detected");
        int length = this.readWord(2);
        int sample = this.readWord(1);
        int rows = this.readWord(2);
        int cols = this.readWord(2);
        int components = this.readWord(1);
        checkState(components == 3, "%s components not supported", components);
        int y = this.readWord(3);
        int cb = this.readWord(3);
        int cr = this.readWord(3);
        checkState(y == 0x012200 && cb == 0x021101 && cr == 0x031101);
        this.writeWord(os, 0xffc0, 2);
        this.writeWord(os, length, 2);
        this.writeWord(os, sample, 1);
        this.writeWord(os, rows, 2);
        this.writeWord(os, cols, 2);
        this.writeWord(os, components, 1);
        this.writeWord(os, y, 3);
        this.writeWord(os, cb, 3);
        this.writeWord(os, cr, 3);
        System.out.printf("Image size %dx%d [%d,%d]\n", rows, cols, length, this.bytesRead);
    }

    private void readHuffmanTable(OutputStream os) {
        // DHT, pdf P40 B.2.4.2
        while (this.readWord(2, 2) == 0xffc4) {
            int length = this.readWord(2);
            int id = this.readWord(1);
            byte[] bytes = this.read(length - 3);
            var huffman = new Huffman(bytes);
            if ((id & 0x10) == 0 && (id & 0x01) == 0) {
                this.dc0 = huffman;
                huffman.setName("DC0");
            } else if ((id & 0x10) == 0 && (id & 0x01) != 0) {
                this.dc1 = huffman;
                huffman.setName("DC1");
            } else if ((id & 0x10) != 0 && (id & 0x01) == 0) {
                this.ac0 = huffman;
                huffman.setName("AC0");
            } else if ((id & 0x10) != 0 && (id & 0x01) != 0) {
                this.ac1 = huffman;
                huffman.setName("AC1");
            } else
                throw new IllegalStateException("Huffman table id not supported");
            this.writeWord(os, 0xffc4, 2);
            this.writeWord(os, length, 2);
            this.writeWord(os, id, 1);
            this.write(os, bytes);
            System.out.printf("Huffman table %s%d found [%d,%d]\n",
                    (id & 0x10) == 0 ? "DC" : "AC", id & 0x01, length, this.bytesRead);
            // System.out.println(huffman);
        }
        this.rewind(2);
    }

    private void readScanMarker(OutputStream os) {
        // SOS, pdf P37 B.2.3
        checkState(this.readWord(2) == 0xffda, "SOS not detected");
        int length = this.readWord(2);
        int components = this.readWord(1);
        checkState(components == 3, "%s components not supported", components);
        int y = this.readWord(2);
        int cb = this.readWord(2);
        int cr = this.readWord(2);
        checkState(y == 0x0100 && cb == 0x0211 && cr == 0x0311);
        this.writeWord(os, 0xffda, 2);
        this.writeWord(os, length, 2);
        this.writeWord(os, components, 1);
        this.writeWord(os, y, 2);
        this.writeWord(os, cb, 2);
        this.writeWord(os, cr, 2);
        this.copy(os, 3);
        System.out.printf("Read scan start [%d,%d]\n", length, this.bytesRead);
    }

    private void readScan() {
        int startAt = this.bytesRead;
        this.scan_current = this.nextByteInScan();
        this.scan_offset = 0;
        try {
            while (true) {
                for (int s = 0; s < 4; s++)
                    this.componentY.add(this.readBlock(this.dc0, this.ac0));
                this.componentCb.add(this.readBlock(this.dc1, this.ac1));
                this.componentCr.add(this.readBlock(this.dc1, this.ac1));
            }
        } catch (NoSuchElementException | Huffman.NotFoundException e) {
            int mask = this.mask(8 - this.scan_offset);
            if ((this.scan_current & mask) == mask) {
                System.out.printf("Read scan end Y:Cb:Cr - %d:%d:%d [%d,%d]\n",
                        this.componentY.size(), this.componentCb.size(), this.componentCr.size(),
                        this.bytesRead - startAt, this.bytesRead);
                return;
            }
            throw e;
        }
    }

    private int[] readBlock(Huffman dcHuffman, Huffman acHuffman) {
        int[] block = new int[64], zeroHolder = new int[1];
        block[0] = this.readDcValue(dcHuffman);
        int pos = 1;
        while (pos < 64) {
            int symbol = this.readAcValue(acHuffman, zeroHolder);
            if (zeroHolder[0] == 0 && symbol == 0)  // EOB
                break;
            pos += zeroHolder[0];
            block[pos++] = symbol;
        }
        return block;
    }

    private int nextByteInScan() {
        int b = this.readWord(1, 2);
        if (b != 0xff)
            return b;
        int bb = this.readWord(1);
        if (bb == 0x00)
            return b;
        this.rewind(2);
        throw new NoSuchElementException();
    }

    private int readDcValue(Huffman huffman) {
        var res = huffman.findSymbol(this.scan_current, this.scan_offset, this::nextByteInScan);
        this.scan_current = res.current;
        this.scan_offset = res.offset;
        return this.readValueInCategory(res.symbol & 0xff);
    }

    private int readAcValue(Huffman huffman, int[] zeroHolder) {
        var res = huffman.findSymbol(this.scan_current, this.scan_offset, this::nextByteInScan);
        zeroHolder[0] = (res.symbol & 0xf0) >> 4;
        this.scan_current = res.current;
        this.scan_offset = res.offset;
        return this.readValueInCategory(res.symbol & 0x0f);
    }

    private int readValueInCategory(int category) {
        if (category == 0)
            return 0;
        int bits = 8 - this.scan_offset;
        int value = this.scan_current & this.mask(bits);
        while (bits < category) {
            this.scan_current = this.nextByteInScan();
            bits += 8;
            value = (value << 8) | this.scan_current;
        }
        this.scan_offset = (this.scan_offset + category) % 8;
        if (this.scan_offset == 0)
            this.scan_current = this.nextByteInScan();
        int symbol = value >> (bits - category);
        if ((symbol & (1 << (category - 1))) != 0)
            return symbol;
        return 1 - (int) pow(2, category) + symbol;
    }

    protected void depredictAndDequantize() {
        this.depredictAndDequantize(this.componentY, this.quantizationTable0);
        this.depredictAndDequantize(this.componentCb, this.quantizationTable1);
        this.depredictAndDequantize(this.componentCr, this.quantizationTable1);
    }

    private void depredictAndDequantize(List<int[]> component, int[] table) {
        int lastDcValue = 0;
        for (int[] block : component) {
            block[0] += lastDcValue;
            lastDcValue = block[0];
            for (int i = 0; i < 64; i++)
                block[i] *= table[i];
        }
    }

    protected void quantizeAndPredict() {
        this.quantizeAndPredict(this.componentY, this.quantizationTable0);
        this.quantizeAndPredict(this.componentCb, this.quantizationTable1);
        this.quantizeAndPredict(this.componentCr, this.quantizationTable1);
    }

    private void quantizeAndPredict(List<int[]> component, int[] table) {
        int lastDcValue = 0;
        for (int[] block : component) {
            for (int i = 0; i < 64; i++)
                block[i] /= table[i];
            int temp = block[0];
            block[0] -= lastDcValue;
            lastDcValue = temp;
        }
    }

    private void writeScan(OutputStream os) {
        int startAt = this.bytesWritten;
        this.scan_current = 0;
        this.scan_offset = 0;
        int i = 0, j = 0, k = 0;
        while (i < this.componentY.size()) {
            for (int s = 0; s < 4; s++)
                this.writeBlock(os, this.componentY.get(i++), this.dc0, this.ac0);
            this.writeBlock(os, this.componentCb.get(j++), this.dc1, this.ac1);
            this.writeBlock(os, this.componentCr.get(k++), this.dc1, this.ac1);
        }
        if (this.scan_offset > 0)
            this.writeByteInScan(os, 0xff & this.mask(8 - this.scan_offset), 8 - this.scan_offset);
        System.out.printf("Write scan end [%d,%d]\n", this.bytesWritten - startAt, this.bytesWritten);
        for (var entry : this.dcFreqStats.entrySet()) {
            System.out.printf("%s categories entropy: %f, distribution: %s\n",
                    entry.getKey().getName(), entropy(entry.getValue().values()), entry.getValue().values());
        }
    }

    private void writeBlock(OutputStream os, int[] block, Huffman dcHuffman, Huffman acHuffman) {
        int[] bitsHolder = new int[1];
        int value = this.encodeDcValue(block[0], dcHuffman, bitsHolder);
        this.writeByteInScan(os, value, bitsHolder[0]);
        int last = 0;
        for (int i = 1; i < 64; i++) {
            if (block[i] == 0)
                continue;
            int zeros = i - last - 1;
            while (zeros >= 16) {
                value = this.encodeAcValue(15, 0, acHuffman, bitsHolder);
                this.writeByteInScan(os, value, bitsHolder[0]);
                zeros -= 16;
            }
            value = this.encodeAcValue(zeros, block[i], acHuffman, bitsHolder);
            this.writeByteInScan(os, value, bitsHolder[0]);
            last = i;
        }
        value = this.encodeAcValue(0, 0, acHuffman, bitsHolder);
        this.writeByteInScan(os, value, bitsHolder[0]);
    }

    private void writeByteInScan(OutputStream os, int value, int bits) {
        this.scan_current = (this.scan_current << bits) | value;
        this.scan_offset += bits;
        while (this.scan_offset >= 8) {
            bits = this.scan_offset - 8;
            value = (this.scan_current & ~this.mask(bits)) >> bits;
            this.writeWord(os, value, 1);
            if (value == 0xff)
                this.writeWord(os, 0x00, 1);
            this.scan_current &= this.mask(bits);
            this.scan_offset -= 8;
        }
    }

    private int encodeDcValue(int symbol, Huffman huffman, int[] bitsHolder) {
        return this.encodeValueInRunningCategory(0, symbol, 11, huffman, bitsHolder);
    }

    private int encodeAcValue(int zeros, int symbol, Huffman huffman, int[] bitsHolder) {
        return this.encodeValueInRunningCategory(zeros, symbol, 10, huffman, bitsHolder);
    }

    private int encodeValueInRunningCategory(int zeros, int symbol, int maxCategory, Huffman huffman, int[] bitsHolder) {
        this.dcFreqStats.computeIfAbsent(huffman, h -> new HashMap<>())
                .compute(symbol, (s, c) -> c != null ? c + 1 : 1);
        int absSymbol = abs(symbol);
        for (int i = 0; i <= maxCategory; i++) {
            if (absSymbol < (int) pow(2, i)) {
                int code = huffman.findCode((byte) ((zeros << 4) | i), bitsHolder);
                bitsHolder[0] += i;
                if (symbol < 0)
                    symbol += (int) pow(2, i) - 1;
                return (code << i) | symbol;
            }
        }
        throw new IllegalStateException(String.format("Unexpected DC/AC value %d:%d", zeros, symbol));
    }

    /*
     * --------------------
     * Auxiliary functions
     * --------------------
     */

    private byte[] read(int n) {
        try {
            byte[] bytes = this.is.readNBytes(n);
            this.bytesRead += n;
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void write(OutputStream os, byte[] bytes) {
        try {
            os.write(bytes);
            this.bytesWritten += bytes.length;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void copy(OutputStream os, int n) {
        try {
            os.write(this.is.readNBytes(n));
            this.bytesRead += n;
            this.bytesWritten += n;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void rewind(int n) {
        try {
            this.is.reset();
            this.bytesRead -= n;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int readWord(int n) {
        return this.readWord(n, 0);
    }

    private int readWord(int n, int mark) {
        try {
            checkArgument(n <= 4 && n > 0);
            if (mark > 0)
                this.is.mark(mark);
            byte[] bytes = this.is.readNBytes(n);
            int word = 0;
            for (int i = 0; i < n; i++)
                word |= (bytes[i] & 0xff) << (n - i - 1) * 8;
            this.bytesRead += n;
            return word;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeWord(OutputStream os, int word, int n) {
        try {
            checkArgument(n <= 4 && n > 0);
            for (int i = (n - 1) * 8; i >= 0; i -= 8)
                os.write((byte) (word >> i));
            this.bytesWritten += n;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int mask(int lsb) {
        checkArgument(lsb >= 0 && lsb <= 30);
        return (int) pow(2, lsb) - 1;
    }

    private double entropy(Collection<Integer> frequences) {
        int sum = frequences.stream().mapToInt(f -> f).sum();
        return frequences.stream().mapToInt(f -> f)
                .filter(f -> f > 0)
                .mapToDouble(f -> 1.0 * f / sum * Math.log(1.0 * sum / f) / Math.log(2))
                .sum();
    }

    public static void main(String[] args) throws IOException {
        var jpeg = new Jpeg("./Lenna.jpg", "./Lenna.out");
        jpeg.recompress();
    }
}
