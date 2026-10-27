package org.example.capstone_groupproject_csc311;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ImageCaptioner {
    private static final int SAMPLE_SIZE = 64;
    private static final int MAX_COLOR_CLUSTERS = 4;
    private static final int MAX_CLUSTERING_ITERATIONS = 30;
    private static final double MIN_CLUSTER_DISTANCE = 5.0;

    public String generateCaption(Image image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("An image is required to generate a caption.");
        }

        PixelReader pixelReader = image.getPixelReader();
        if (pixelReader == null) {
            throw new IllegalArgumentException("The image does not contain readable pixel data.");
        }

        List<Sample> samples = new ArrayList<>(SAMPLE_SIZE * SAMPLE_SIZE);
        double totalBrightness = 0;

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

                samples.add(new Sample(pixel, column, row));
                totalBrightness += pixel.getBrightness();
            }
        }

        if (samples.isEmpty()) {
            throw new IllegalArgumentException("The image has no visible pixels to describe.");
        }

        Map<String, ColorStats> colors = clusterColors(samples);
        Map.Entry<String, ColorStats> dominant = colors.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().count))
                .orElseThrow();

        double brightness = totalBrightness / samples.size();
        String lighting = brightness > 0.68 ? "bright" : brightness < 0.32 ? "dark" : "moderately lit";
        double dominantShare = (double) dominant.getValue().count / samples.size();
        StringBuilder caption = new StringBuilder("A ")
                .append(lighting)
                .append(" image ");
        caption.append(dominantShare >= 0.4 ? "dominated by " : "containing mostly ")
                .append(dominant.getKey());

        Map.Entry<String, ColorStats> secondary = colors.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(dominant.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getValue().count))
                .orElse(null);
        if (secondary != null && (double) secondary.getValue().count / samples.size() >= 0.12) {
            caption.append(", with ")
                    .append(secondary.getKey())
                    .append(" areas mostly ")
                    .append(locationOf(secondary.getValue()));
        }

        if (hasTexturedGreenery(samples)) {
            caption.append(". Textured green areas may be leafy trees or other vegetation");
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

    private boolean hasTexturedGreenery(List<Sample> samples) {
        int greenCount = 0;
        int occupiedTiles = 0;
        boolean[][] greenTiles = new boolean[8][8];
        double brightnessTotal = 0;
        double brightnessSquaredTotal = 0;

        for (Sample sample : samples) {
            Color color = sample.color;
            double hue = color.getHue();
            if (hue < 65 || hue > 155 || color.getSaturation() < 0.25
                    || color.getBrightness() < 0.12) {
                continue;
            }

            greenCount++;
            brightnessTotal += color.getBrightness();
            brightnessSquaredTotal += color.getBrightness() * color.getBrightness();
            int tileX = Math.min(7, sample.x / (SAMPLE_SIZE / 8));
            int tileY = Math.min(7, sample.y / (SAMPLE_SIZE / 8));
            if (!greenTiles[tileY][tileX]) {
                greenTiles[tileY][tileX] = true;
                occupiedTiles++;
            }
        }

        if ((double) greenCount / samples.size() < 0.12 || occupiedTiles < 10) {
            return false;
        }

        double meanBrightness = brightnessTotal / greenCount;
        double variance = brightnessSquaredTotal / greenCount - meanBrightness * meanBrightness;
        return variance >= 0.0064;
    }

    public String generateCaption(Image image, List<String> likelySubjects) {
        String caption = generateCaption(image);
        if (likelySubjects == null) {
            return caption;
        }
        List<String> subjects = likelySubjects.stream()
                .filter(subject -> subject != null && !subject.isBlank())
                .map(subject -> subject.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(3)
                .toList();
        if (subjects.isEmpty()) {
            return caption;
        }
        StringBuilder description = new StringBuilder(caption)
                .append(" The scene may include ")
                .append(joinSubjects(subjects))
                .append('.');

        String subject = subjects.getFirst();
        if (hasAnyWord(subject, "forest", "woodland", "grove", "jungle", "rainforest",
                "park", "garden", "meadow", "lawn", "field", "tree", "leaf", "foliage",
                "plant", "orchard", "conifer", "pine", "oak", "maple", "flower")) {
            if (colorShare(image, "green") >= 0.10) {
                description.append(" Green areas may be foliage or grass.");
            }
        } else if (hasAnyWord(subject, "snow", "glacier", "iceberg", "alpine", "alp", "mountain",
                "ski slope", "ski resort")) {
            if (colorShare(image, "white") >= 0.10) {
                description.append(" Bright areas may be snow or ice.");
            }
        }
        return description.toString();
    }

    private boolean hasAnyWord(String text, String... words) {
        for (String word : words) {
            if (text.equals(word) || text.startsWith(word + " ")
                    || text.endsWith(" " + word) || text.contains(" " + word + " ")) {
                return true;
            }
        }
        return false;
    }

    private double colorShare(Image image, String targetColor) {
        PixelReader pixelReader = image.getPixelReader();
        int matches = 0;
        int visible = 0;
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
                visible++;
                if (targetColor.equals(classifyColor(pixel))) {
                    matches++;
                }
            }
        }
        return visible == 0 ? 0 : (double) matches / visible;
    }

    private String joinSubjects(List<String> subjects) {
        if (subjects.size() == 1) {
            return subjects.getFirst();
        }
        if (subjects.size() == 2) {
            return subjects.get(0) + " or " + subjects.get(1);
        }
        return String.join(", ", subjects.subList(0, subjects.size() - 1))
                + ", or " + subjects.getLast();
    }

    private Map<String, ColorStats> clusterColors(List<Sample> samples) {
        List<Cluster> clusters = initializeClusters(samples);

        for (int iteration = 0; iteration < MAX_CLUSTERING_ITERATIONS; iteration++) {
            for (Cluster cluster : clusters) {
                cluster.clear();
            }
            for (Sample sample : samples) {
                closestCluster(sample, clusters).add(sample);
            }

            boolean changed = false;
            for (Cluster cluster : clusters) {
                if (cluster.count > 0 && cluster.updateCenter()) {
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        Map<String, ColorStats> colors = new HashMap<>();
        for (Cluster cluster : clusters) {
            if (cluster.count == 0) {
                continue;
            }
            Color centerColor = Color.color(
                    cluster.totalRed / cluster.count,
                    cluster.totalGreen / cluster.count,
                    cluster.totalBlue / cluster.count);
            String colorName = classifyColor(centerColor);
            colors.computeIfAbsent(colorName, ignored -> new ColorStats())
                    .add(cluster.count, cluster.totalX, cluster.totalY);
        }
        return colors;
    }

    private List<Cluster> initializeClusters(List<Sample> samples) {
        List<Cluster> clusters = new ArrayList<>(MAX_COLOR_CLUSTERS);
        clusters.add(new Cluster(sampleClosestToMean(samples)));

        while (clusters.size() < MAX_COLOR_CLUSTERS) {
            Sample farthest = null;
            double farthestDistance = 0;
            for (Sample sample : samples) {
                double nearestDistance = Double.POSITIVE_INFINITY;
                for (Cluster cluster : clusters) {
                    nearestDistance = Math.min(nearestDistance, distanceSquared(sample.lab, cluster.center));
                }
                if (nearestDistance > farthestDistance) {
                    farthest = sample;
                    farthestDistance = nearestDistance;
                }
            }
            if (farthest == null || farthestDistance < MIN_CLUSTER_DISTANCE * MIN_CLUSTER_DISTANCE) {
                break;
            }
            clusters.add(new Cluster(farthest));
        }
        return clusters;
    }

    private Sample sampleClosestToMean(List<Sample> samples) {
        double meanL = 0;
        double meanA = 0;
        double meanB = 0;
        for (Sample sample : samples) {
            meanL += sample.lab[0];
            meanA += sample.lab[1];
            meanB += sample.lab[2];
        }
        meanL /= samples.size();
        meanA /= samples.size();
        meanB /= samples.size();

        Sample first = samples.getFirst();
        double closestToMean = distanceSquared(first.lab, meanL, meanA, meanB);
        for (Sample sample : samples) {
            double distance = distanceSquared(sample.lab, meanL, meanA, meanB);
            if (distance < closestToMean) {
                first = sample;
                closestToMean = distance;
            }
        }
        return first;
    }

    private Cluster closestCluster(Sample sample, List<Cluster> clusters) {
        Cluster closest = clusters.getFirst();
        double closestDistance = distanceSquared(sample.lab, closest.center);
        for (int index = 1; index < clusters.size(); index++) {
            Cluster candidate = clusters.get(index);
            double distance = distanceSquared(sample.lab, candidate.center);
            if (distance < closestDistance) {
                closest = candidate;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private double distanceSquared(double[] first, double[] second) {
        double dL = first[0] - second[0];
        double dA = first[1] - second[1];
        double dB = first[2] - second[2];
        return dL * dL + dA * dA + dB * dB;
    }

    private double distanceSquared(double[] lab, double l, double a, double b) {
        double dL = lab[0] - l;
        double dA = lab[1] - a;
        double dB = lab[2] - b;
        return dL * dL + dA * dA + dB * dB;
    }

    private double[] toLab(Color color) {
        double red = linearize(color.getRed());
        double green = linearize(color.getGreen());
        double blue = linearize(color.getBlue());

        double x = (red * 0.4124564 + green * 0.3575761 + blue * 0.1804375) / 0.95047;
        double y = red * 0.2126729 + green * 0.7151522 + blue * 0.0721750;
        double z = (red * 0.0193339 + green * 0.1191920 + blue * 0.9503041) / 1.08883;
        double fx = labPivot(x);
        double fy = labPivot(y);
        double fz = labPivot(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private double linearize(double component) {
        return component <= 0.04045
                ? component / 12.92
                : Math.pow((component + 0.055) / 1.055, 2.4);
    }

    private double labPivot(double value) {
        return value > 0.008856
                ? Math.cbrt(value)
                : 7.787 * value + 16.0 / 116;
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
        double horizontalOffset = Math.abs(centerX - 0.5);
        double verticalOffset = Math.abs(centerY - 0.5);

        if (horizontalOffset < 0.12 && verticalOffset < 0.12) {
            return "near the center";
        }
        if (horizontalOffset > verticalOffset) {
            return centerX < 0.5 ? "on the left" : "on the right";
        }
        if (verticalOffset > 0.12) {
            return centerY < 0.5 ? "near the top" : "near the bottom";
        }
        return centerX < 0.5 ? "on the left" : "on the right";
    }

    private class Sample {
        private final Color color;
        private final int x;
        private final int y;
        private final double[] lab;

        private Sample(Color color, int x, int y) {
            this.color = color;
            this.x = x;
            this.y = y;
            this.lab = toLab(color);
        }
    }

    private static class Cluster {
        private double[] center;
        private double totalL;
        private double totalA;
        private double totalB;
        private double totalRed;
        private double totalGreen;
        private double totalBlue;
        private long totalX;
        private long totalY;
        private int count;

        private Cluster(Sample seed) {
            center = seed.lab.clone();
        }

        private void clear() {
            totalL = 0;
            totalA = 0;
            totalB = 0;
            totalRed = 0;
            totalGreen = 0;
            totalBlue = 0;
            totalX = 0;
            totalY = 0;
            count = 0;
        }

        private void add(Sample sample) {
            totalL += sample.lab[0];
            totalA += sample.lab[1];
            totalB += sample.lab[2];
            totalRed += sample.color.getRed();
            totalGreen += sample.color.getGreen();
            totalBlue += sample.color.getBlue();
            totalX += sample.x;
            totalY += sample.y;
            count++;
        }

        private boolean updateCenter() {
            double[] updated = {totalL / count, totalA / count, totalB / count};
            boolean changed = distanceSquared(center, updated) > 0.01;
            center = updated;
            return changed;
        }

        private static double distanceSquared(double[] first, double[] second) {
            double dL = first[0] - second[0];
            double dA = first[1] - second[1];
            double dB = first[2] - second[2];
            return dL * dL + dA * dA + dB * dB;
        }
    }

    private static class ColorStats {
        private int count;
        private long totalX;
        private long totalY;

        private void add(int additionalCount, long additionalX, long additionalY) {
            count += additionalCount;
            totalX += additionalX;
            totalY += additionalY;
        }
    }
}
