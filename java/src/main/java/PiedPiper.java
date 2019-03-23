import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class PiedPiperEncoder extends Jpeg {
    private List<int[]> prevComponentY = new ArrayList<>();
    private List<int[]> prevComponentCb = new ArrayList<>();
    private List<int[]> prevComponentCr = new ArrayList<>();

    PiedPiperEncoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
    }

    @Override
    protected void quantizeAndPredict() {
        List<int[]> prevComponentY = this.componentY.stream()
                .map(z -> Arrays.copyOf(z, 64)).collect(Collectors.toList());
        List<int[]> prevComponentCb = this.componentCb.stream()
                .map(z -> Arrays.copyOf(z, 64)).collect(Collectors.toList());
        List<int[]> prevComponentCr = this.componentCr.stream()
                .map(z -> Arrays.copyOf(z, 64)).collect(Collectors.toList());
        this.quantizeAndPredict(this.componentY, this.prevComponentY,
                this.quantizationTable0, true, this.width / 4);
        this.quantizeAndPredict(this.componentCb, this.prevComponentCb,
                this.quantizationTable1, false, this.width / 16);
        this.quantizeAndPredict(this.componentCr, this.prevComponentCr,
                this.quantizationTable1, false, this.width / 16);
        this.prevComponentY = prevComponentY;
        this.prevComponentCb = prevComponentCb;
        this.prevComponentCr = prevComponentCr;
    }

    private void quantizeAndPredict(List<int[]> component, List<int[]> prevComponent,
                                    int[] table, boolean y, int w) {
        for (int i = component.size() - 1; i >= 0; i--) {
            double prediction = PiedPiper.predict(i, component, prevComponent, y, w);
            int[] zigzag = component.get(i);
            zigzag[0] = zigzag[0] / table[0] - (int) Math.round(prediction / table[0]);
            for (int j = 1; j < 64; j++)
                zigzag[j] /= table[j];
//            component.set(i, ZigZag.transform(zigzag));
        }
    }
}

class PiedPiperDecoder extends Jpeg {
    private List<int[]> prevComponentY = new ArrayList<>();
    private List<int[]> prevComponentCb = new ArrayList<>();
    private List<int[]> prevComponentCr = new ArrayList<>();

    PiedPiperDecoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
    }

    @Override
    protected void depredictAndDequantize() {
        this.depredictAndDequantize(this.componentY, this.prevComponentY,
                this.quantizationTable0, true, this.width / 4);
        this.depredictAndDequantize(this.componentCb, this.prevComponentCb,
                this.quantizationTable1, false, this.width / 16);
        this.depredictAndDequantize(this.componentCr, this.prevComponentCr,
                this.quantizationTable1, false, this.width / 16);
        this.prevComponentY.clear();
        for (int[] zigzag : this.componentY)
            this.prevComponentY.add(Arrays.copyOf(zigzag, 64));
        this.prevComponentCb.clear();
        for (int[] zigzag : this.componentCb)
            this.prevComponentCb.add(Arrays.copyOf(zigzag, 64));
        this.prevComponentCr.clear();
        for (int[] zigzag : this.componentCr)
            this.prevComponentCr.add(Arrays.copyOf(zigzag, 64));
    }

    private void depredictAndDequantize(List<int[]> component, List<int[]> prevComponent,
                                        int[] table, boolean y, int w) {
        for (int i = 0; i < component.size(); i++) {
            int[] zigzag = component.get(i);
            for (int j = 1; j < 64; j++)
                zigzag[j] *= table[j];
            double prediction = PiedPiper.predict(i, component, prevComponent, y, w);
            zigzag[0] = (zigzag[0] + (int) Math.round(prediction / table[0])) * table[0];
        }
    }
}

class PiedPiper {
    public static double predict(int i, List<int[]> component, List<int[]> prevComponent, boolean y, int w) {
        int[][] block = ZigZag.zigzag2block(component.get(i));
        block[0][0] = 0;
        double[] delta;
        int left = leftBlock(i, y, w), above = aboveBlock(i, y, w);
        if (above >= 0) {
            delta = DctInt.idct1x8(block, 0);
            minus(delta, 0, DctInt.idct1x8(ZigZag.zigzag2block(component.get(above)), 7));
        } else if (above + prevComponent.size() >= 0) {
            delta = DctInt.idct1x8(block, 0);
            minus(delta, 0, DctInt.idct1x8(ZigZag.zigzag2block(prevComponent.get(above + prevComponent.size())), 7));
        } else if (left >= 0) {
            delta = DctInt.idct8x1(block, 0);
            minus(delta, 0, DctInt.idct8x1(ZigZag.zigzag2block(component.get(left)), 7));
            Arrays.sort(delta);
            delta = Arrays.copyOfRange(delta, 3, delta.length - 3);
            return Arrays.stream(delta).average().getAsDouble() * -8;
        } else {
            return 0;
        }
        if (left >= 0) {
            delta = Arrays.copyOf(delta, 16);
            System.arraycopy(DctInt.idct8x1(block, 0), 0, delta, 8, 8);
            minus(delta, 8, DctInt.idct8x1(ZigZag.zigzag2block(component.get(left)), 7));
            Arrays.sort(delta);
            delta = Arrays.copyOfRange(delta, 6, delta.length - 6);
        } else {
            Arrays.sort(delta);
            delta = Arrays.copyOfRange(delta, 3, delta.length - 3);
        }
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

    private static String currentDirectory(String path) {
        if (!path.contains("/"))
            return path;
        return path.substring(path.lastIndexOf("/") + 1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 2) {
            if ("-analysis".equals(args[0])) {
                String inputFile = args[1];
                String outputFile = inputFile.replaceAll("[.].+?$", ".out.jpg");
                new Jpeg(inputFile, currentDirectory(outputFile)).recompress();
                return;
            } else if ("-encode".equals(args[0])) {
                String inputFile = args[1];
                String outputFile = inputFile.replaceAll("[.].+?$", ".jpp");
                new PiedPiperEncoder(inputFile, currentDirectory(outputFile)).recompress();
                return;
            } else if ("-decode".equals(args[0])) {
                String inputFile = args[1];
                String outputFile = inputFile.replaceAll("[.].+?$", ".out.jpg");
                new PiedPiperDecoder(inputFile, currentDirectory(outputFile)).recompress();
                return;
            } else if ("-arithmetic".equals(args[0])) {
                String inputFile = args[1];
                String outputFile = inputFile.replaceAll("[.].+?$", ".out.jpg");
                new JpegArithEncoder(inputFile, currentDirectory(outputFile)).recompress();
                return;
            }
        }
        System.err.println("Usage: java PiedPiper -analysis <InputFile>");
        System.err.println("Usage: java PiedPiper -encode <InputFile>");
        System.err.println("Usage: java PiedPiper -decode <InputFile>");
        System.err.println("Usage: java PiedPiper -arithmetic <InputFile>");
        System.exit(1);
    }
}