module org.example.capstone_groupproject_csc311 {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.capstone_groupproject_csc311 to javafx.fxml;
    exports org.example.capstone_groupproject_csc311;
}