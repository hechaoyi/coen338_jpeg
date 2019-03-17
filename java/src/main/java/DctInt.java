import java.util.stream.IntStream;

public class DctInt {
    private static final double cucvZero = 1 / Math.sqrt(2);

    public static double[] idct8x1(int[][] input, int x) {
        return IntStream.range(0, 8).mapToDouble(y -> idctKernel(input, x, y)).toArray();
    }


    public static double[] idct1x8(int[][] input, int y) {
        return IntStream.range(0, 8).mapToDouble(x -> idctKernel(input, x, y)).toArray();
    }

    private static double idctKernel(int[][] input, int x, int y) {
        double sum = 0;
        for (int v = 0; v < 8; v++) {
            double cv = (v == 0) ? cucvZero : 1;
            for (int u = 0; u < 8; u++) {
                double cu = (u == 0) ? cucvZero : 1;
                sum += cu * cv * input[v][u] * kernel(x, y, u, v);
            }
        }
        return sum / 4 + 128;
    }

    private static double kernel(int x, int y, int u, int v) {
        return Math.cos(((2 * x + 1) * u * Math.PI) / 16) * Math.cos(((2 * y + 1) * v * Math.PI) / 16);
    }
}
