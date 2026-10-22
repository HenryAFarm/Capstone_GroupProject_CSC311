package org.example.capstone_groupproject_csc311;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class HelloController {
    @FXML
    private Label welcomeText;

    @FXML
    private TextArea textToSpeak;

    @FXML
    private Button speakButton;

    private final TextToSpeechService textToSpeechService = new TextToSpeechService();

    @FXML
    private void speakText() {
        String text = textToSpeak.getText();
        if (text == null || text.isBlank()) {
            welcomeText.setText("Type something in the box to hear it spoken.");
            return;
        }

        speakButton.setDisable(true);
        new Thread(() -> {
            try {
                textToSpeechService.speak(text);
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    welcomeText.setText("Text-to-speech could not run: " + e.getMessage());
                    speakButton.setDisable(false);
                });
                return;
            }

            Platform.runLater(() -> {
                welcomeText.setText("Voice playing.");
                speakButton.setDisable(false);
            });
        }).start();
    }
}
