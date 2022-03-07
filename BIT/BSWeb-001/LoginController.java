package BSWeb.controllers.admin;

import BSWeb.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;


@Controller
@RequestMapping("/admin")
public class LoginController {

    @Value("${spring.datasource.url}")
    private String db_url;
    @Value("${spring.datasource.username}")
    private String db_username;
    @Value("${spring.datasource.password}")
    private String db_password;
    @Value("${spring.datasource.driver-class-name}")
    private String db_driver_class_name;

    @Autowired
    private User user;

    @GetMapping("/login")
    public String show_auth_form(@RequestParam(value = "invalid_password", required = false) Integer invalid_password,
                                 Model model)
    {
        model.addAttribute("invalid_password", invalid_password);
        return "admin/loginPage";
    }

    // защита от sql-инъекций не реализована!
    @PostMapping("/login")
    public String check_form(@RequestParam("login") String input_login,
                             @RequestParam("password") String input_password,
                             Model model) throws ClassNotFoundException, SQLException
    {

        Class.forName(db_driver_class_name);
        Connection connection = DriverManager.getConnection(db_url, db_username, db_password);
        Statement statement = connection.createStatement();

        String sql_query = "SELECT access FROM admins " +
                "WHERE login='" + input_login + "' AND " +
                "password='" + input_password + "';";

        ResultSet resultSet = statement.executeQuery(sql_query);

        if (resultSet.next()) {

            user.setAccess_level(resultSet.getInt("access"));
            return "redirect:/admin/panel";
        }

        return "redirect:/admin/login?invalid_password=1";
    }


}