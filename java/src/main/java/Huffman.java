import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.pow;

public class Huffman {
    private Map<Byte, Integer> symbol2code = new HashMap<>();
    private Map<Integer, Byte> code2symbol = new HashMap<>();
    private Map<Integer, Integer> codeLength = new HashMap<>();
    private int[] lengthList;
    private String name;

    Huffman(byte[] bytes) {
        int code = 0, pos = 16;
        for (int i = 0; i < 16; i++) {
            code <<= 1;
            int count = bytes[i] & 0xff;
            for (int j = 0; j < count; j++) {
                byte symbol = bytes[pos++];
                checkState((code & ~this.mask(i + 1)) == 0);
                symbol2code.put(symbol, code);
                code2symbol.put(code, symbol);
                codeLength.put(code, i + 1);
                code++;
            }
        }
        this.lengthList = codeLength.values().stream().mapToInt(Integer::intValue).distinct().sorted().toArray();
    }

    public int findCode(byte symbol, int[] bitsHolder) {
        checkState(this.symbol2code.containsKey(symbol));
        int code = this.symbol2code.get(symbol);
        bitsHolder[0] = this.codeLength.get(code);
        return code;
    }

    public Result findSymbol(int current, int offset, IntSupplier next) {
        int bits = 8 - offset;
        int value = current & this.mask(bits);
        for (int length : this.lengthList) {
            if (length > bits) {
                current = next.getAsInt();
                bits += 8;
                value = (value << 8) | current;
            }
            int code = value >> (bits - length);
            if (this.codeLength.getOrDefault(code, 0) == length) {
                offset = (offset + length) % 8;
                if (offset == 0)
                    current = next.getAsInt();
                return new Result(this.code2symbol.get(code), current, offset);
            }
        }
        throw new NotFoundException(String.format("Unknown code %d [%d], cannot be found in huffman table\n%s",
                value, bits, this));
    }

    private int mask(int lsb) {
        checkArgument(lsb >= 0 && lsb <= 30);
        return (int) pow(2, lsb) - 1;
    }

    static class Result {
        byte symbol;
        int current;
        int offset;

        Result(byte symbol, int current, int offset) {
            this.symbol = symbol;
            this.current = current;
            this.offset = offset;
        }
    }

    static class NotFoundException extends IllegalStateException {
        NotFoundException(String s) {
            super(s);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return String.format("symbol2code: %s\ncode2symbol: %s\ncodeLength: %s\nlengthList: %s",
                this.symbol2code, this.code2symbol, this.codeLength, Arrays.toString(this.lengthList));
    }
}
