package org.example.capstone_groupproject_csc311;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class ImageCaptioner {
    private static final int SAMPLE_SIZE = 64;

    public String generateCaption(Image image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("An image is required to generate a caption.");
        }

        PixelReader pixelReader = image.getPixelReader();
        if (pixelReader == null) {
            throw new IllegalArgumentException("The image does not contain readable from pixel data.");
        }

        Map<String, ColorStats> colors = new HashMap<>();
        double totalBrightness = 0;
        int sampledPixels = 0;

        for (int row = 0; row < SAMPLE_SIZE; row++) {
            int y = Math.min((int) ((row + 0.5) * image.getHeight() / SAMPLE_SIZE),
                    (int) image.getHeight() - 1);
            for (int column = 0; column < SAMPLE_SIZE; column++) {
                int x = Math.min((int) ((column + 0.5) * image.getWidth() / SAMPLE_SIZE),
                        (int) image.getWidth() - 1);
                Color pixel = pixelReader.getColor(x, y);
                if (pixel.getOpacity() < 0.15) {
                    continue;
                }

                String colorName = classifyColor(pixel);
                ColorStats stats = colors.computeIfAbsent(colorName, ignored -> new ColorStats());
                stats.add(column, row);
                totalBrightness += pixel.getBrightness();
                sampledPixels++;
            }
        }

        if (sampledPixels == 0) {
            throw new IllegalArgumentException("The image has no visible pixels to describe.");
        }

        Map.Entry<String, ColorStats> dominant = colors.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().count))
                .orElseThrow();

        double brightness = totalBrightness / sampledPixels;
        String lighting = brightness > 0.68 ? "bright" : brightness < 0.32 ? "dark" : "moderately lit";
        double dominantShare = (double) dominant.getValue().count / sampledPixels;
        StringBuilder caption = new StringBuilder("A ")
                .append(lighting)
                .append(" image ");
        caption.append(dominantShare >= 0.4 ? "dominated by " : "containing mostly ")
                .append(dominant.getKey());

        Map.Entry<String, ColorStats> secondary = colors.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(dominant.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getValue().count))
                .orElse(null);
        if (secondary != null && (double) secondary.getValue().count / sampledPixels >= 0.12) {
            caption.append(", with ")
                    .append(secondary.getKey())
                    .append(" areas mostly ")
                    .append(locationOf(secondary.getValue()));
        }

        if (image.getWidth() > image.getHeight() * 1.2) {
            caption.append(". The image is landscape-oriented.");
        } else if (image.getHeight() > image.getWidth() * 1.2) {
            caption.append(". The image is portrait-oriented.");
        } else {
            caption.append(". The image is roughly square.");
        }

        return caption.toString();
    }

    private String classifyColor(Color color) {
        double brightness = color.getBrightness();
        if (brightness < 0.18) {
            return "black";
        }
        if (color.getSaturation() < 0.18) {
            return brightness > 0.82 ? "white" : "gray";
        }

        double hue = color.getHue();
        if (hue < 15 || hue >= 345) {
            return "red";
        }
        if (hue < 45) {
            return "orange";
        }
        if (hue < 70) {
            return "yellow";
        }
        if (hue < 165) {
            return "green";
        }
        if (hue < 200) {
            return "cyan";
        }
        if (hue < 255) {
            return "blue";
        }
        if (hue < 290) {
            return "purple";
        }
        return "pink";
    }

    private String locationOf(ColorStats stats) {
        double centerX = stats.totalX / (double) stats.count / (SAMPLE_SIZE - 1);
        double centerY = stats.totalY / (double) stats.count / (SAMPLE_SIZE - 1);

        if (centerX < 0.38) {
            return "on the left";
        }
        if (centerX > 0.62) {
            return "on the right";
        }
        if (centerY < 0.38) {
            return "near the top";
        }
        if (centerY > 0.62) {
            return "near the bottom";
        }
        return "near the center";
    }

    private static class ColorStats {
        private int count;
        private long totalX;
        private long totalY;

        private void add(int x, int y) {
            count++;
            totalX += x;
            totalY += y;
        }
    }
}
