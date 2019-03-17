import java.util.Arrays;
import java.util.stream.IntStream;

public class ZigZag {
    public static int[][] zigzag2block(int[] zigzag) {
        int[][] block = new int[8][8];
        int n = 0;
        for (int i = 0; i < 8; i++) {
            if (i % 2 == 0) {
                for (int j = 0; j <= i; j++)
                    block[i - j][j] = zigzag[n++];
            } else {
                for (int j = i; j >= 0; j--)
                    block[i - j][j] = zigzag[n++];
            }
        }
        for (int i = 1; i < 8; i++) {
            if (i % 2 == 1) {
                for (int j = i; j < 8; j++)
                    block[i + 7 - j][j] = zigzag[n++];
            } else {
                for (int j = 7; j >= i; j--)
                    block[i + 7 - j][j] = zigzag[n++];
            }
        }
        return block;
    }

    public static void main(String[] args) {
        int[] input = IntStream.range(0, 64).toArray();
        System.out.println(Arrays.deepToString(zigzag2block(input)));
    }
}
