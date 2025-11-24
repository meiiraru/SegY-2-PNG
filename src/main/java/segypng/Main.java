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
        ArgsOptions.parse(args);

        String filePath = ArgsOptions.INPUT_FILE.getAsString();
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
            float minAmplitude = ArgsOptions.AMPLITUDE.getAsFloat(0);
            float maxAmplitude = ArgsOptions.AMPLITUDE.getAsFloat(1);
            boolean recalculateAmplitude = minAmplitude >= maxAmplitude;

            float[][] amplitudes = new float[width][height];
            for (int x = 0; x < width; x++) {
                float[] values = traces.get(x).getValues();
                int localHeight = Math.min(height, values.length);
                for (int y = 0; y < localHeight; y++) {
                    float v = values[y];
                    amplitudes[x][y] = v;
                    if (recalculateAmplitude) {
                        if (v < minAmplitude) minAmplitude = v;
                        if (v > maxAmplitude) maxAmplitude = v;
                    }
                }
            }

            System.out.printf("Data amplitudes range from %.2f to %.2f%n", minAmplitude, maxAmplitude);

            //create the image
            float scaleX = ArgsOptions.SCALE.getAsFloat(0);
            float scaleY = ArgsOptions.SCALE.getAsFloat(1);
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
                    float amplitude = top * (1.0f - wy) + bottom * wy;

                    //map amplitude to [-1,0,+1] where -1 is minAmplitude, 0 is 0, +1 is maxAmplitude
                    if (amplitude >= 0) {
                        amplitude = amplitude / maxAmplitude;
                    } else {
                        amplitude = -(amplitude / minAmplitude);
                    }

                    //convert to [-1,1] and get the color
                    Color seismicColor = getSeismicColor(amplitude);
                    image.setRGB(x, y, seismicColor.getRGB());
                }
            }

            //write the image to a file
            String out = ArgsOptions.OUTPUT_FILE.getAsString();
            ImageIO.write(image, "png", new File(out));

            System.out.println("Successfully converted SEGY file to " + out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}