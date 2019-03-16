import java.util.Arrays;

public class Dct {
    private static final double cucvZero = 1 / Math.sqrt(2);

    public static double[][] dct(double[][] input) {
        double[][] output = new double[8][8];
        for (int u = 0; u < 8; u++) {
            for (int v = 0; v < 8; v++) {
                double sum = 0;
                for (int x = 0; x < 8; x++)
                    for (int y = 0; y < 8; y++)
                        sum += (input[x][y] - 128) * kernel(x, y, u, v);
                double cu = (u == 0) ? cucvZero : 1, cv = (v == 0) ? cucvZero : 1;
                output[u][v] = (cu * cv) / 4 * sum;
            }
        }
        return output;
    }

    public static double[][] idct(double[][] input) {
        double[][] output = new double[8][8];
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                double sum = 0;
                for (int u = 0; u < 8; u++) {
                    for (int v = 0; v < 8; v++) {
                        double cu = (u == 0) ? cucvZero : 1, cv = (v == 0) ? cucvZero : 1;
                        sum += cu * cv * input[u][v] * kernel(x, y, u, v);
                    }
                }
                output[x][y] = sum / 4 + 128;
            }
        }
        return output;
    }

    private static double kernel(int x, int y, int u, int v) {
        return Math.cos(((2 * x + 1) * u * Math.PI) / 16) * Math.cos(((2 * y + 1) * v * Math.PI) / 16);
    }

    private static double[][] int2double(int[][] input) {
        double[][] output = new double[8][8];
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                output[i][j] = input[i][j];
        return output;
    }

    private static int[][] double2int(double[][] input) {
        int[][] output = new int[8][8];
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                output[i][j] = (int) Math.round(input[i][j]);
        return output;
    }

    public static void main(String[] args) {
        int[][] origin = {
                {139, 144, 149, 153, 155, 155, 155, 155},
                {144, 151, 153, 156, 159, 156, 156, 156},
                {150, 155, 160, 163, 158, 156, 156, 156},
                {159, 161, 162, 160, 160, 159, 159, 159},
                {159, 160, 161, 162, 162, 155, 155, 155},
                {161, 161, 161, 161, 160, 157, 157, 157},
                {162, 162, 161, 163, 162, 157, 157, 157},
                {162, 162, 161, 161, 163, 158, 158, 158}
        };
        double[][] middle = dct(int2double(origin));
        int[][] restored = double2int(idct(middle));
        System.out.println(Arrays.deepToString(origin));
        System.out.println(Arrays.deepToString(middle));
        System.out.println(Arrays.deepToString(restored));
        System.out.println(Arrays.deepEquals(origin, restored));

        int[][] origin2 = {
                {236, -1, -12, -5, 2, -2, -3, 1},
                {-23, -17, -6, -3, -3, 0, 0, -1},
                {-11, -9, -2, 2, 0, -1, -1, 0},
                {-7, -2, 0, 1, 1, 0, 0, 0},
                {-1, -1, 1, 2, 0, -1, 1, 1},
                {2, 0, 2, 0, -1, 1, 1, -1},
                {-1, 0, 0, -1, 0, 2, 1, -1},
                {-3, 2, -4, -2, 2, 1, -1, 0}
        };
        double[][] middle2 = idct(int2double(origin2));
        int[][] restored2 = double2int(dct(middle2));
        System.out.println(Arrays.deepToString(origin2));
        System.out.println(Arrays.deepToString(middle2));
        System.out.println(Arrays.deepToString(restored2));
        System.out.println(Arrays.deepEquals(origin2, restored2));

        origin2[0][0] = 0;
        double[][] middle3 = idct(int2double(origin2));
        double delta = 0;
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                delta += middle2[i][j] - middle3[i][j];
        System.out.println(Math.round(delta / 8));

//        int[][] origin3 = new int[8][8];
//        for (int i = 0; i < 8; i++)
//            Arrays.fill(origin3[i], 255);
//        double[][] middle3 = dct(int2double(origin3));
//        System.out.println(Arrays.deepToString(origin3));
//        System.out.println(Arrays.deepToString(double2int(middle3)));
    }
}
