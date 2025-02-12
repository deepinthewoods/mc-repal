package ninja.trek;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.*;

public class ImageProcessor {
    private static final Map<Color, Color> colorMappingCache = new HashMap<>();

    public static BufferedImage processImage(BufferedImage input, List<Color> targetPalette, int contrast, int saturation, int hue) {
        if (targetPalette.isEmpty()) {
            Repal.LOGGER.info("empty palette");
            return input;
        }

        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);

        // Pre-process adjustments
        float contrastFactor = (100.0f + contrast) / 100.0f;
        float saturationFactor = (100.0f + saturation) / 100.0f;
        float hueShift = hue / 100.0f * 360.0f; // Convert -100 to 100 range to -360 to 360 degrees

        // Process each pixel
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                Color inputColor = new Color(input.getRGB(x, y), true);

                // Skip fully transparent pixels
                if (inputColor.getAlpha() == 0) {
                    output.setRGB(x, y, inputColor.getRGB());
                    continue;
                }

                // Apply pre-processing adjustments
                Color adjustedColor = adjustColor(inputColor, contrastFactor, saturationFactor, hueShift);

                // Find closest palette color
                Color mappedColor = colorMappingCache.computeIfAbsent(adjustedColor,
                        color -> findClosestPaletteColor(color, targetPalette));

                // Preserve original alpha
                Color finalColor = new Color(
                        mappedColor.getRed(),
                        mappedColor.getGreen(),
                        mappedColor.getBlue(),
                        inputColor.getAlpha()
                );

                output.setRGB(x, y, finalColor.getRGB());
            }
        }
        return output;
    }

    private static Color adjustColor(Color input, float contrastFactor, float saturationFactor, float hueShift) {
        float[] hsb = Color.RGBtoHSB(input.getRed(), input.getGreen(), input.getBlue(), null);

        // Adjust hue (normalized to 0-1 range)
        hsb[0] = (hsb[0] + (hueShift / 360.0f)) % 1.0f;
        if (hsb[0] < 0) hsb[0] += 1.0f; // Handle negative hue values

        // Adjust saturation
        hsb[1] = Math.max(0.0f, Math.min(1.0f, hsb[1] * saturationFactor));

        // Adjust contrast (using brightness)
        float adjustedBrightness = ((hsb[2] - 0.5f) * contrastFactor) + 0.5f;
        hsb[2] = Math.max(0.0f, Math.min(1.0f, adjustedBrightness));

        // Convert back to RGB
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

    private static Color findClosestPaletteColor(Color input, List<Color> palette) {
        Color closestColor = palette.get(0);
        double minDistance = Double.MAX_VALUE;

        for (Color paletteColor : palette) {
            double distance = calculateColorDistance(input, paletteColor);
            if (distance < minDistance) {
                minDistance = distance;
                closestColor = paletteColor;
            }
        }

        return closestColor;
    }

    private static double calculateColorDistance(Color c1, Color c2) {
        // Using CIE76 color difference formula
        double[] lab1 = rgbToLab(c1);
        double[] lab2 = rgbToLab(c2);

        double deltaL = lab1[0] - lab2[0];
        double deltaA = lab1[1] - lab2[1];
        double deltaB = lab1[2] - lab2[2];

        return Math.sqrt(deltaL * deltaL + deltaA * deltaA + deltaB * deltaB);
    }

    private static double[] rgbToLab(Color color) {
        // RGB to XYZ
        double r = color.getRed() / 255.0;
        double g = color.getGreen() / 255.0;
        double b = color.getBlue() / 255.0;

        r = r > 0.04045 ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = g > 0.04045 ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = b > 0.04045 ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) * 100;
        double y = (r * 0.2126 + g * 0.7152 + b * 0.0722) * 100;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) * 100;

        // XYZ to Lab
        x /= 95.047;
        y /= 100.000;
        z /= 108.883;

        x = x > 0.008856 ? Math.pow(x, 1.0/3.0) : (7.787 * x) + 16.0/116.0;
        y = y > 0.008856 ? Math.pow(y, 1.0/3.0) : (7.787 * y) + 16.0/116.0;
        z = z > 0.008856 ? Math.pow(z, 1.0/3.0) : (7.787 * z) + 16.0/116.0;

        return new double[] {
                (116 * y) - 16,  // L
                500 * (x - y),   // a
                200 * (y - z)    // b
        };
    }

    public static void clearCache() {
        colorMappingCache.clear();
    }
}