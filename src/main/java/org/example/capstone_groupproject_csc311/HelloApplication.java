package org.example.capstone_groupproject_csc311;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("UI-view.fxml"));
        Scene mainScene = new Scene(fxmlLoader.load(), 800, 600);
        UIController controller = fxmlLoader.getController();
        stage.setOnHidden(event -> controller.close());

        URL splashImageUrl = HelloApplication.class.getResource(
                "/images_/Screenshot 2026-10-22 105826.png");
        if (splashImageUrl == null) {
            throw new IOException("Splash screen image was not found in the images_ resources folder.");
        }

        ImageView splashImage = new ImageView(new Image(splashImageUrl.toExternalForm()));
        splashImage.setFitWidth(760);
        splashImage.setFitHeight(520);
        splashImage.setPreserveRatio(true);

        Label title = new Label("Image describer");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        VBox splashContent = new VBox(12, splashImage, title);
        splashContent.setAlignment(javafx.geometry.Pos.CENTER);

        stage.setScene(new Scene(splashContent, 800, 600));
        stage.setTitle("Image describer");
        stage.show();

        PauseTransition splashDuration = new PauseTransition(Duration.seconds(2.5));
        splashDuration.setOnFinished(event -> {
            event.consume();
            stage.setScene(mainScene);
        });
        splashDuration.play();
    }
}
