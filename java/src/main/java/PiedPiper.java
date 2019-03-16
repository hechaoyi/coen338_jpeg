import java.io.IOException;

class PiedPiperEncoder extends Jpeg {
    public PiedPiperEncoder(String inputFileName, String outputFileName) {
        super(inputFileName, outputFileName);
    }

    @Override
    protected void quantizeAndPredict() {
        // TODO
//        this.quantizeAndPredict(this.componentY, this.quantizationTable0);
        this.quantizeAndPredict(this.componentCb, this.quantizationTable1);
        this.quantizeAndPredict(this.componentCr, this.quantizationTable1);
    }
}

//class PiedPiperDecoder extends Jpeg {
//    public PiedPiperDecoder(String inputFileName, String outputFileName) {
//        super(inputFileName, outputFileName);
//    }
//}

class PiedPiper {
    public static void main(String[] args) throws IOException {
        var ppe = new PiedPiperEncoder("./Lenna.jpg", "./Lenna.jpp");
        ppe.recompress();
//        var ppd = new PiedPiperDecoder("./Lenna.jpp", "./Lenna.out");
//        ppd.recompress();
    }
}