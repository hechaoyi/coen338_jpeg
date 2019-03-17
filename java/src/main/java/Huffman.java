import java.util.*;
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
    private byte[] bytes;

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
        this.bytes = bytes;
    }

    Huffman(int[] frequencies) {
        this(TreeNode.build(frequencies));
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
                if (offset == 0) {
                    try {
                        current = next.getAsInt();
                    } catch (NoSuchElementException e) {
                        offset = 8;
                    }
                }
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

    public byte[] getBytes() {
        return bytes;
    }

    public String toString() {
        return String.format("symbol2code: %s\ncode2symbol: %s\ncodeLength: %s\nlengthList: %s",
                this.symbol2code, this.code2symbol, this.codeLength, Arrays.toString(this.lengthList));
    }

    public static void main(String[] args) {
        int[] input1 = {528, 762, 605, 390, 185, 30};
        System.out.println(new Huffman(input1));
        int[] input2 = {383, 514, 269, 81, 1, 2};
        System.out.println(new Huffman(input2));
    }
}

class TreeNode implements Comparable<TreeNode> {
    private final int symbol;
    private final int freq;
    private final TreeNode left, right;

    TreeNode(int symbol, int freq, TreeNode left, TreeNode right) {
        this.symbol = symbol;
        this.freq = freq;
        this.left = left;
        this.right = right;
    }

    boolean isLeaf() {
        return left == null && right == null;
    }

    @Override
    public int compareTo(TreeNode o) {
        return this.freq - o.freq;
    }

    static byte[] build(int[] frequencies) {
        var heap = new PriorityQueue<TreeNode>();
        for (int i = 0; i < frequencies.length; i++)
            if (frequencies[i] > 0)
                heap.add(new TreeNode(i, frequencies[i], null, null));
        int count = heap.size();
        while (heap.size() > 1) {
            var right = heap.remove();
            var left = heap.remove();
            heap.add(new TreeNode(0, left.freq + right.freq, left, right));
        }
        var queue = new ArrayDeque<>(heap);
        var result = new ArrayList<List<Integer>>();
        while (!queue.isEmpty()) {
            var level = new ArrayList<Integer>();
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                var node = queue.remove();
                if (node.isLeaf()) {
                    level.add(node.symbol);
                } else {
                    queue.add(node.left);
                    queue.add(node.right);
                }
            }
            result.add(level);
        }
        byte[] bytes = new byte[16 + count];
        int pos = 16;
        for (int i = 1; i < result.size(); i++) {
            if (result.get(i).isEmpty())
                continue;
            bytes[i - 1] = (byte) result.get(i).size();
            for (int value : result.get(i))
                bytes[pos++] = (byte) value;
        }
        return bytes;
    }
}