/*
 * Reference arithmetic coding
 * Copyright (c) Project Nayuki
 *
 * https://www.nayuki.io/page/reference-arithmetic-coding
 * https://github.com/nayuki/Reference-arithmetic-coding
 */
package arithmetic;

import java.io.*;


/**
 * Compression application using adaptive arithmetic coding.
 * <p>Usage: java AdaptiveArithmeticCompress InputFile OutputFile</p>
 * <p>Then use the corresponding "AdaptiveArithmeticDecompress" application to recreate the original input file.</p>
 * <p>Note that the application starts with a flat frequency table of 257 symbols (all set to a frequency of 1),
 * and updates it after each byte encoded. The corresponding decompressor program also starts with a flat
 * frequency table and updates it after each byte decoded. It is by design that the compressor and
 * decompressor have synchronized states, so that the data can be decompressed properly.</p>
 */
public class AdaptiveArithmeticCompress {

    public static void main(String[] args) throws IOException {
        String file = "images/IMG_2073.jpeg";
        String file1 = file.replaceAll("[.].+?$", ".ari");
        String file2 = file.replaceAll("[.].+?$", ".out.jpg");
        File inputFile = new File(file);
        File outputFile1 = new File(file1);
        File outputFile2 = new File(file2);

        // Perform file compression
        try (InputStream in = new BufferedInputStream(new FileInputStream(inputFile));
             BitOutputStream out = new BitOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile1)))) {
            compress(in, out);
        }

        // Perform file decompression
        try (BitInputStream in = new BitInputStream(new BufferedInputStream(new FileInputStream(outputFile1)));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile2))) {
            decompress(in, out);
        }
    }


    // To allow unit testing, this method is package-private instead of private.
    static void compress(InputStream in, BitOutputStream out) throws IOException {
        FlatFrequencyTable initFreqs = new FlatFrequencyTable(257);
        FrequencyTable freqs = new SimpleFrequencyTable(initFreqs);
        ArithmeticEncoder enc = new ArithmeticEncoder(32, out);
        while (true) {
            // Read and encode one byte
            int symbol = in.read();
            if (symbol == -1)
                break;
            enc.write(freqs, symbol);
            freqs.increment(symbol);
        }
        enc.write(freqs, 256);  // EOF
        enc.finish();  // Flush remaining code bits
    }


    // To allow unit testing, this method is package-private instead of private.
    static void decompress(BitInputStream in, OutputStream out) throws IOException {
        FlatFrequencyTable initFreqs = new FlatFrequencyTable(257);
        FrequencyTable freqs = new SimpleFrequencyTable(initFreqs);
        ArithmeticDecoder dec = new ArithmeticDecoder(32, in);
        while (true) {
            // Decode and write one byte
            int symbol = dec.read(freqs);
            if (symbol == 256)  // EOF symbol
                break;
            out.write(symbol);
            freqs.increment(symbol);
        }
    }

}
