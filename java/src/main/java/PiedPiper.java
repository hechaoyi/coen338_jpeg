import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

class PiedPiperEncoder extends Jpeg {
    public PiedPiperEncoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
    }

    @Override
    protected void quantizeAndPredict() {
        this.quantizeAndPredict(this.componentY, this.quantizationTable0, true, this.width / 4);
        this.quantizeAndPredict(this.componentCb, this.quantizationTable1, false, this.width / 16);
        this.quantizeAndPredict(this.componentCr, this.quantizationTable1, false, this.width / 16);
    }

    private void quantizeAndPredict(List<int[]> component, int[] table, boolean y, int w) {
        for (int i = component.size() - 1; i > 0; i--) {
            double prediction = PiedPiper.predict(i, component::get, y, w);
            int[] zigzag = component.get(i);
            zigzag[0] = zigzag[0] / table[0] - (int) Math.round(prediction / table[0]);
            for (int j = 1; j < 64; j++)
                zigzag[j] /= table[j];
        }
        int[] zigzag = component.get(0);
        for (int i = 0; i < 64; i++)
            zigzag[i] /= table[i];
    }
}

class PiedPiperDecoder extends Jpeg {
    public PiedPiperDecoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
    }

    @Override
    protected void depredictAndDequantize() {
        this.depredictAndDequantize(this.componentY, this.quantizationTable0, true, this.width / 4);
        this.depredictAndDequantize(this.componentCb, this.quantizationTable1, false, this.width / 16);
        this.depredictAndDequantize(this.componentCr, this.quantizationTable1, false, this.width / 16);
    }

    private void depredictAndDequantize(List<int[]> component, int[] table, boolean y, int w) {
        int[] zigzag = component.get(0);
        for (int i = 0; i < 64; i++)
            zigzag[i] *= table[i];
        for (int i = 1; i < component.size(); i++) {
            zigzag = component.get(i);
            for (int j = 1; j < 64; j++)
                zigzag[j] *= table[j];
            double prediction = PiedPiper.predict(i, component::get, y, w);
            zigzag[0] = (zigzag[0] + (int) Math.round(prediction / table[0])) * table[0];
        }
    }
}

class PiedPiper {
    public static double predict(int i, Function<Integer, int[]> zigzagGetter, boolean y, int w) {
        int[] zigzag = zigzagGetter.apply(i);
        int[][] block = ZigZag.zigzag2block(zigzag);
        block[0][0] = 0;
        int left = leftBlock(i, y, w), above = aboveBlock(i, y, w);
        double[] delta;
        if (left < 0) {
            delta = DctInt.idct1x8(block, 0);
            minus(delta, 0, DctInt.idct1x8(ZigZag.zigzag2block(zigzagGetter.apply(above)), 7));
        } else if (above < 0) {
            delta = DctInt.idct8x1(block, 0);
            minus(delta, 0, DctInt.idct8x1(ZigZag.zigzag2block(zigzagGetter.apply(left)), 7));
        } else {
            delta = Arrays.copyOf(DctInt.idct8x1(block, 0), 16);
            System.arraycopy(DctInt.idct1x8(block, 0), 0, delta, 8, 8);
            minus(delta, 0, DctInt.idct8x1(ZigZag.zigzag2block(zigzagGetter.apply(left)), 7));
            minus(delta, 8, DctInt.idct1x8(ZigZag.zigzag2block(zigzagGetter.apply(above)), 7));
        }
        Arrays.sort(delta);
        delta = Arrays.copyOfRange(delta, 3, delta.length - 3);
        return Arrays.stream(delta).average().getAsDouble() * -8;
    }

    private static int leftBlock(int i, boolean y, int w) {
        if (y) {
            if (i % 4 == 1 || i % 4 == 3)
                return i - 1;
            int res = i - 3;
            return (res / w == i / w) ? res : -1;
        } else {
            int res = i - 1;
            return (res / w == i / w) ? res : -1;
        }
    }

    private static int aboveBlock(int i, boolean y, int w) {
        if (y) {
            if (i % 4 == 2 || i % 4 == 3)
                return i - 2;
            return i - w + 2;
        } else {
            return i - w;
        }
    }

    private static void minus(double[] src, int srcPos, double[] dst) {
        for (int i = 0; i < dst.length; i++)
            src[srcPos + i] -= dst[i];
    }

    public static void main(String[] args) throws IOException {
        var ppe = new PiedPiperEncoder("./Lenna.jpg", "./Lenna.jpp");
        ppe.recompress();
        var ppd = new PiedPiperDecoder("./Lenna.jpp", "./Lenna.out.jpg");
        ppd.recompress();
    }
}