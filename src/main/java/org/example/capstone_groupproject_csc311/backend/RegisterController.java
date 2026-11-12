package org.example.capstone_groupproject_csc311.backend;

import org.example.capstone_groupproject_csc311.UserProfile.ProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegisterController {
    private final ProfileService userService;

    public RegisterController(ProfileService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "password", required = false) String password,
            Model model) {

        String normalizedUsername = username == null ? "" : username.trim();

        if (normalizedUsername.length() < 3) {
            return showError(model, "Username must be at least 3 characters long.",
                    normalizedUsername);
        }
        if (password == null || password.length() < 6) {
            return showError(model, "Password must be at least 6 characters long.",
                    normalizedUsername);
        }
        if (!userService.register(normalizedUsername, password)) {
            return showError(model, "That username is already taken.",
                    normalizedUsername);
        }

        return "redirect:/login?registered";
    }

    private String showError(Model model, String error, String username) {
        model.addAttribute("error", error);
        model.addAttribute("username", username);
        return "register";
    }
}
