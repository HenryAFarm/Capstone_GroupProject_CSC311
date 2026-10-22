package org.example.capstone_groupproject_csc311;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.List;

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
        statusLabel.setText("Selected: " + file.getName());
        return true;
    }

}
