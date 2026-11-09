module org.example.capstone_groupproject_csc311 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.microsoft.onnxruntime;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    opens org.example.capstone_groupproject_csc311.backend;
    opens org.example.capstone_groupproject_csc311.service to javafx.fxml;
    exports org.example.capstone_groupproject_csc311;
}