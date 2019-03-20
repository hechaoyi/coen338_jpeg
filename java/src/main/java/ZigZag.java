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

//    private static int[] mapping = {
//            0, 1, 2, 3, 5, 6, 9, 10, 14, 15, 20, 21, 27, 28, 35,
//            4, 7, 8, 11, 12, 13, 16, 17, 18, 19, 22, 23, 24, 25, 26, 29, 30, 31, 32, 33, 34,
//            36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63};
    private static int[] mapping = {
        0, 1, 2, 5, 3, 6, 4, 9, 14, 10, 15, 7, 8, 20, 27, 21, 28, 13, 11, 35, 12, 16, 19, 26, 17, 18, 22, 29, 34, 25, 23, 42, 24, 36, 30, 33, 31, 32, 41, 37, 43, 40, 38, 48, 39, 44, 47, 45, 46, 53, 49, 52, 50, 51, 54, 57, 55, 56, 60, 58, 59, 61, 62, 63};

    public static int[] transform(int[] input) {
        int[] output = new int[64];
        for (int i = 0; i < 64; i++)
            output[i] = input[mapping[i]];
        return output;
    }


    public static void main(String[] args) {
        int[] input = IntStream.range(0, 64).toArray();
        System.out.println(Arrays.deepToString(zigzag2block(input)));
    }
}
