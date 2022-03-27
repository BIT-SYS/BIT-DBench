package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;

public class LoginFormController {

    public TextField txtUserName;
    public PasswordField txtPassword;
    public Button btnLogin;

    public void btnLogin_OnAction(ActionEvent event) throws ClassNotFoundException, IOException {

        if (!isValidated()) {
            new Alert(Alert.AlertType.ERROR, "Invalid username and password, please try again").show();
            txtUserName.requestFocus();
            return;
        }

        String username = txtUserName.getText();
        String password = txtPassword.getText();

        Class.forName("com.mysql.cj.jdbc.Driver");
        try {
            Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/dep8_hello", "root", "mysql");

            String sql = String.format("SELECT * FROM user WHERE username='%s' AND password='%s'", username, password);
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery(sql);

            if (rst.next()){
                AnchorPane root = FXMLLoader.load(this.getClass().getResource("/view/ManageCustomerForm.fxml"));
                Scene mainScene = new Scene(root);
                Stage primaryStage = (Stage)btnLogin.getScene().getWindow();
                primaryStage.setScene(mainScene);
                primaryStage.setTitle("Hello JDBC: Home");
                primaryStage.sizeToScene();
                primaryStage.centerOnScreen();
            }else{
                new Alert(Alert.AlertType.ERROR, "Invalid username and password, please try again").show();
                txtUserName.requestFocus();
            }

            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isValidated(){
        return (txtUserName.getText().trim().length() >= 2 &&
            txtPassword.getText().trim().length() >= 2);
    }

}
