package org.example.capstone_groupproject_csc311;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LocalImageClassifier implements AutoCloseable {
    private static final int INPUT_SIZE = 224;
    private static final int TOP_LABEL_COUNT = 1;
    private static final double MIN_LABEL_CONFIDENCE = 0.35;
    private static final String MODEL_REVISION = "c5903ea5f5bb219f02c85a778af0fedf14f502d8";
    private static final String MODEL_URL = "https://huggingface.co/onnx-community/mobilenet_v2_1.0_224/resolve/"
            + MODEL_REVISION + "/onnx/model_quantized.onnx";
    private static final String LABELS_URL = "https://huggingface.co/onnx-community/mobilenet_v2_1.0_224/resolve/"
            + MODEL_REVISION + "/config.json";
    private static final String MODEL_SHA256 = "fa1251c88a258b3f897bf3718f224e29956206a6878f8651d29e474bbff23630";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private OrtSession session;
    private List<String> labels;

    public synchronized List<String> classify(Image image) throws IOException, InterruptedException {
        if (image == null || image.getPixelReader() == null) {
            throw new IllegalArgumentException("The image does not contain readable pixel data.");
        }

        ensureModelReady();
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(createInput(image)),
                new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
             OrtSession.Result results = session.run(Map.of(session.getInputNames().iterator().next(), inputTensor))) {
            Object output = results.get(0).getValue();
            if (!(output instanceof float[][] scores) || scores.length == 0) {
                throw new IOException("The local image model returned an unsupported result.");
            }
            return topLabels(scores[0]);
        } catch (OrtException exception) {
            throw new IOException("The local image model could not analyze this image.", exception);
        }
    }

    private void ensureModelReady() throws IOException, InterruptedException {
        if (session != null) {
            return;
        }

        Path modelDirectory = Path.of(System.getProperty("user.home"), ".image-describer", "models");
        Files.createDirectories(modelDirectory);
        Path modelPath = modelDirectory.resolve("mobilenet-v2-int8.onnx");
        Path labelsPath = modelDirectory.resolve("mobilenet-v2-labels.json");

        if (!Files.exists(modelPath) || !MODEL_SHA256.equals(sha256(modelPath))) {
            download(MODEL_URL, modelPath);
            if (!MODEL_SHA256.equals(sha256(modelPath))) {
                Files.deleteIfExists(modelPath);
                throw new IOException("The downloaded image model failed its integrity check.");
            }
        }
        if (!Files.exists(labelsPath)) {
            download(LABELS_URL, labelsPath);
        }

        labels = readLabels(labelsPath);
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            session = environment.createSession(modelPath.toString(), options);
        } catch (OrtException exception) {
            throw new IOException("The local image model could not be loaded.", exception);
        }
    }

    private void download(String source, Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "ImageDescriber/1.0")
                .GET()
                .build();
        HttpResponse<Path> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofFile(destination.resolveSibling(destination.getFileName() + ".part")));
        Path temporaryPath = destination.resolveSibling(destination.getFileName() + ".part");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(temporaryPath);
            throw new IOException("Could not download the free local image model (HTTP "
                    + response.statusCode() + "). Check your internet connection.");
        }
        Files.move(temporaryPath, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
        }
    }

    private List<String> readLabels(Path configPath) throws IOException {
        JsonNode idToLabel = JSON.readTree(configPath.toFile()).path("id2label");
        if (!idToLabel.isObject() || idToLabel.isEmpty()) {
            throw new IOException("The local image model's label file is invalid.");
        }
        Map<Integer, String> labelsById = new TreeMap<>();
        var fields = idToLabel.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                labelsById.put(Integer.parseInt(entry.getKey()), entry.getValue().asText());
            } catch (NumberFormatException exception) {
                throw new IOException("The local image model's label file contains an invalid class ID.", exception);
            }
        }
        List<String> result = new ArrayList<>(labelsById.size());
        for (int index = 0; index < labelsById.size(); index++) {
            String label = labelsById.get(index);
            if (label == null || label.isBlank()) {
                throw new IOException("The local image model's label file is missing a class.");
            }
            result.add(label);
        }
        return result;
    }

    private float[] createInput(Image image) {
        PixelReader pixelReader = image.getPixelReader();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        double cropSize = Math.min(width, height) * INPUT_SIZE / 256.0;
        double left = (width - cropSize) / 2.0;
        double top = (height - cropSize) / 2.0;
        FloatBuffer input = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE);

        for (int channel = 0; channel < 3; channel++) {
            for (int y = 0; y < INPUT_SIZE; y++) {
                int sourceY = Math.min(height - 1,
                        (int) (top + (y + 0.5) * cropSize / INPUT_SIZE));
                for (int x = 0; x < INPUT_SIZE; x++) {
                    int sourceX = Math.min(width - 1,
                            (int) (left + (x + 0.5) * cropSize / INPUT_SIZE));
                    Color pixel = pixelReader.getColor(sourceX, sourceY);
                    double component = switch (channel) {
                        case 0 -> pixel.getRed();
                        case 1 -> pixel.getGreen();
                        default -> pixel.getBlue();
                    };
                    input.put((float) (component * 2.0 - 1.0));
                }
            }
        }
        input.rewind();
        return input.array();
    }

    private List<String> topLabels(float[] scores) throws IOException {
        if (scores.length != labels.size()) {
            throw new IOException("The local image model's labels do not match its output.");
        }
        double maxScore = Float.NEGATIVE_INFINITY;
        for (float score : scores) {
            maxScore = Math.max(maxScore, score);
        }

        double total = 0;
        double[] probabilities = new double[scores.length];
        for (int index = 0; index < scores.length; index++) {
            probabilities[index] = Math.exp(scores[index] - maxScore);
            total += probabilities[index];
        }

        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < scores.length; index++) {
            indices.add(index);
        }
        indices.sort(Comparator.comparingDouble((Integer index) -> probabilities[index]).reversed());

        List<String> predictions = new ArrayList<>();
        for (int index : indices) {
            if (predictions.size() == TOP_LABEL_COUNT) {
                break;
            }
            if (index == 0 || probabilities[index] / total < MIN_LABEL_CONFIDENCE) {
                continue;
            }
            String label = displayLabel(labels.get(index).split(",", 2)[0].trim());
            if (!label.isBlank() && !predictions.contains(label)) {
                predictions.add(label);
            }
        }
        return predictions;
    }

    private String displayLabel(String label) {
        return switch (label) {
            case "alp" -> "mountain";
            case "lakeside" -> "lake";
            case "seashore" -> "coast";
            default -> label;
        };
    }

    @Override
    public synchronized void close() {
        if (session != null) {
            try {
                session.close();
                session = null;
            } catch (OrtException exception) {
                throw new IllegalStateException("Could not release the local image model.", exception);
            }
        }
    }
}
