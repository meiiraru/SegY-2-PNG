package segypng;

import com.github.thecoldwine.sigrun.common.SEGYStream;
import com.github.thecoldwine.sigrun.common.SEGYStreamFactory;
import com.github.thecoldwine.sigrun.common.SeismicTrace;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderFormatBuilder;
import com.github.thecoldwine.sigrun.serialization.FormatEntry;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderFormatBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    private static final Map<Float, Color> colors = Map.of(
            1f,    new Color(255, 255, 0),
            1/3f,  new Color(191, 0,   0),
            0.2f,  new Color(97,  69,  0),
            0f,    new Color(204, 204, 204),
            -0.2f, new Color(77,  77,  77),
            -1/3f, new Color(0,   0,   191),
            -1f,   new Color(161, 255, 255)
    );

    private static Color getSeismicColor(float value) {
        float lowerKey = -1f;
        float upperKey = 1f;

        for (float key : colors.keySet()) {
            if (key <= value && key > lowerKey)
                lowerKey = key;
            if (key >= value && key < upperKey)
                upperKey = key;
        }

        Color lowerColor = colors.get(lowerKey);
        Color upperColor = colors.get(upperKey);

        if (value == lowerKey)
            return lowerColor;
        if (value == upperKey)
            return upperColor;

        float delta = (value - lowerKey) / (upperKey - lowerKey);

        int red = (int) (lowerColor.getRed() + delta * (upperColor.getRed() - lowerColor.getRed()));
        int green = (int) (lowerColor.getGreen() + delta * (upperColor.getGreen() - lowerColor.getGreen()));
        int blue = (int) (lowerColor.getBlue() + delta * (upperColor.getBlue() - lowerColor.getBlue()));
        return new Color(red, green, blue);
    }

    public static void main(String... args) {
        String filePath = args.length > 0 ? args[0] : "./input.segy";
        float scaleX = args.length > 1 ? Float.parseFloat(args[1]) : 1f;
        float scaleY = args.length > 2 ? Float.parseFloat(args[2]) : 1f;

        System.out.println("Reading SEGY file: " + filePath);

        try (FileInputStream fis = new FileInputStream(filePath)) {
            FileChannel chan = fis.getChannel();

            //header
            SEGYStreamFactory streamFactory = SEGYStreamFactory.create(
                    Charset.forName("Cp1047"),
                    BinaryHeaderFormatBuilder.aBinaryHeaderFormat()
                            .withLineNumberFormat(FormatEntry.create(4, 8))
                            .withSampleIntervalFormat(FormatEntry.create(16, 18))
                            .withSamplesPerDataTraceFormat(FormatEntry.create(20, 22))
                            .withDataSampleCodeFormat(FormatEntry.create(24, 26))
                            .withSegyFormatRevNumberFormat(FormatEntry.create(300, 302))
                            .withFixedLengthTraceFlagFormat(FormatEntry.create(302, 304))
                            .withNumberOf3200ByteFormat(FormatEntry.create(304, 306))
                            .build(),
                    TraceHeaderFormatBuilder.aTraceHeaderFormat()
                            .withEnsembleNumberFormat(FormatEntry.create(20, 24))
                            .withSourceXFormat(FormatEntry.create(72, 76))
                            .withSourceYFormat(FormatEntry.create(76, 80))
                            .withXOfCDPPositionFormat(FormatEntry.create(180, 184))
                            .withYOfCDPPositionFormat(FormatEntry.create(184, 188))
                            .withNumberOfSamplesFormat(FormatEntry.create(114, 116))
                            .build()
            );

            //read the EBCDIC and binary headers
            SEGYStream segyStream = streamFactory.makeStream(chan, List.of());

            //collect all traces into a list
            List<SeismicTrace> traces = new ArrayList<>();
            segyStream.forEach(traces::add);

            //early exit if no traces found
            if (traces.isEmpty()) {
                System.out.println("No traces found in the SEGY file");
                return;
            }

            //determine seismic dimensions
            int width = traces.size();
            int height = traces.getFirst().getValues().length;

            System.out.printf("Seismic grid: %d traces (width) x %d samples (height)%n", width, height);

            //precompute amplitudes grid and find min/max amplitude for normalization
            float[][] amplitudes = new float[width][height];
            float minAmplitude = Float.MAX_VALUE;
            float maxAmplitude = -Float.MAX_VALUE;
            for (int x = 0; x < width; x++) {
                float[] values = traces.get(x).getValues();
                int localHeight = Math.min(height, values.length);
                for (int y = 0; y < localHeight; y++) {
                    float v = values[y];
                    amplitudes[x][y] = v;
                    if (v < minAmplitude) minAmplitude = v;
                    if (v > maxAmplitude) maxAmplitude = v;
                }
            }
            float amplitudeRange = maxAmplitude - minAmplitude;

            System.out.printf("Data amplitudes range from %.2f to %.2f%n", minAmplitude, maxAmplitude);

            //create the image
            int scaledWidth = Math.max(1, Math.round(width * scaleX));
            int scaledHeight = Math.max(1, Math.round(height * scaleY));
            float invScaleX = 1f / scaleX;
            float invScaleY = 1f / scaleY;

            System.out.printf("Creating image: %d x %d (scale %.2f x %.2f)%n", scaledWidth, scaledHeight, scaleX, scaleY);
            BufferedImage image = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);

            //map seismic data to the scaled pixels using bilinear interpolation
            for (int x = 0; x < scaledWidth; x++) {
                int x0 = (int) Math.floor(x * invScaleX);
                int x1 = Math.min(x0 + 1, width - 1);
                float wx = (x * invScaleX) - x0;

                for (int y = 0; y < scaledHeight; y++) {
                    int y0 = (int) Math.floor(y * invScaleY);
                    int y1 = Math.min(y0 + 1, height - 1);
                    float wy = y * invScaleY - y0;

                    //apply bilinear interpolation
                    float a00 = amplitudes[x0][y0];
                    float a10 = amplitudes[x1][y0];
                    float a01 = amplitudes[x0][y1];
                    float a11 = amplitudes[x1][y1];

                    float top = a00 * (1.0f - wx) + a10 * wx;
                    float bottom = a01 * (1.0f - wx) + a11 * wx;
                    float delta = top * (1.0f - wy) + bottom * wy;

                    //normalize interpolated amplitude to [0,1]
                    float norm = (delta - minAmplitude) / amplitudeRange;
                    norm = Math.clamp(norm, 0f, 1f);

                    //convert to [-1,1] and get the color
                    Color seismicColor = getSeismicColor(norm * 2.0f - 1.0f);
                    image.setRGB(x, y, seismicColor.getRGB());
                }
            }

            //write the image to a file
            Path inputFile = Path.of(filePath);
            String filename = inputFile.getFileName().toString();
            String outputName = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;

            File outputFile = new File("./" + outputName + ".png");
            ImageIO.write(image, "png", outputFile);

            System.out.println("Successfully converted SEGY file to " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}