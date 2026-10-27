module org.example.capstone_groupproject_csc311 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.microsoft.onnxruntime;
    opens org.example.capstone_groupproject_csc311 to javafx.fxml;
    exports org.example.capstone_groupproject_csc311;
}