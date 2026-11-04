package org.example.capstone_groupproject_csc311;

import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class UIController {

    @FXML
    private StackPane imageDropArea;

    @FXML
    private ImageView imagePreview;

    @FXML
    private Label placeholderLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private TextArea captionPreview;

    @FXML
    private Button speakButton;

    private final ImageCaptioner imageCaptioner = new ImageCaptioner();
    private final LocalImageClassifier imageClassifier = new LocalImageClassifier();
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "local-image-analysis");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService speechExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "description-text-to-speech");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> activeAnalysis;
    private long analysisRequestId;
    private boolean descriptionAvailable;

    @FXML
    private void initialize() {
        imageDropArea.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        imageDropArea.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            List<File> files = dragboard.getFiles();
            boolean loaded = !files.isEmpty() && loadImage(files.getFirst());
            event.setDropCompleted(loaded);
            event.consume();
        });
    }

    @FXML
    private void chooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose an image");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Image files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));

        Window window = imageDropArea.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile != null) {
            loadImage(selectedFile);
        }
    }

    private boolean loadImage(File file) {
        Image image = new Image(file.toURI().toString());
        if (image.isError()) {
            statusLabel.setText("Could not load image: " + file.getName());
            return false;
        }

        imagePreview.setImage(image);
        placeholderLabel.setVisible(false);
        placeholderLabel.setManaged(false);
        captionPreview.setText("Analyzing image...");
        descriptionAvailable = false;
        speakButton.setDisable(true);
        statusLabel.setText("Analyzing colors and likely image subjects locally.");

        long requestId = ++analysisRequestId;
        if (activeAnalysis != null) {
            activeAnalysis.cancel(true);
        }

        Task<CaptionResult> task = new Task<>() {
            @Override
            protected CaptionResult call() {
                String colorCaption;
                try {
                    colorCaption = imageCaptioner.generateCaption(image);
                } catch (IllegalArgumentException exception) {
                    return new CaptionResult("", exception.getMessage());
                }
                updateMessage("Running local image recognition (first use may download a 3.7 MB model).");
                try {
                    List<String> likelySubjects = imageClassifier.classify(image);
                    return new CaptionResult(imageCaptioner.generateCaption(image, likelySubjects),
                            "Selected: " + file.getName() + " — analyzed locally.");
                } catch (java.io.IOException exception) {
                    return new CaptionResult(colorCaption,
                            "Local subject recognition unavailable; showing color description. "
                                    + exception.getMessage());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return new CaptionResult(colorCaption,
                            "Local subject recognition was interrupted; showing color description.");
                }
            }
        };
        task.messageProperty().addListener((observable, oldMessage, newMessage) -> {
            if (requestId == analysisRequestId && newMessage != null && !newMessage.isBlank()) {
                statusLabel.setText(newMessage);
            }
        });
        task.setOnSucceeded(event -> {
            if (requestId == analysisRequestId) {
                CaptionResult result = task.getValue();
                captionPreview.setText(result.caption());
                descriptionAvailable = !result.caption().isBlank();
                speakButton.setDisable(!descriptionAvailable);
                statusLabel.setText(result.status());
            }
        });
        task.setOnFailed(event -> {
            if (requestId == analysisRequestId) {
                Throwable failure = task.getException();
                captionPreview.setText("The image description could not be generated.");
                descriptionAvailable = false;
                speakButton.setDisable(true);
                statusLabel.setText("Local image recognition failed: "
                        + (failure == null ? "Unknown analysis error." : failure.getMessage()));
            }
        });
        activeAnalysis = analysisExecutor.submit(task);
        return true;
    }

    @FXML
    private void speakDescription() {
        String description = captionPreview.getText().trim();
        if (description.isEmpty()) {
            return;
        }

        speakButton.setDisable(true);
        speakButton.setText("Speaking...");
        statusLabel.setText("Reading the description aloud.");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                speakWithWindowsSpeech(description);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            speakButton.setText("Read Aloud");
            speakButton.setDisable(!descriptionAvailable);
            if (descriptionAvailable) {
                statusLabel.setText("Finished reading the description aloud.");
            }
        });
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            speakButton.setText("Read Aloud");
            speakButton.setDisable(!descriptionAvailable);
            if (descriptionAvailable) {
                statusLabel.setText("Text-to-speech failed: "
                        + (failure == null ? "Unknown speech error." : failure.getMessage()));
            }
        });
        speechExecutor.submit(task);
    }

    private void speakWithWindowsSpeech(String text) throws IOException, InterruptedException {
        String script = "$text = [System.IO.StreamReader]::new("
                + "[Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8).ReadToEnd(); "
                + "Add-Type -AssemblyName System.Speech; "
                + "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "$synth.Speak($text); $synth.Dispose()";
        Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start();

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(text);
        }

        byte[] output;
        try (InputStream input = process.getInputStream();
             ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream()) {
            input.transferTo(capturedOutput);
            output = capturedOutput.toByteArray();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Windows speech synthesis exited with code " + exitCode + ": "
                    + new String(output, StandardCharsets.UTF_8).trim());
        }
    }

    public void close() {
        analysisExecutor.shutdownNow();
        speechExecutor.shutdownNow();
        imageClassifier.close();
    }

    private record CaptionResult(String caption, String status) {
    }

}
