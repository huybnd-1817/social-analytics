package com.sunasterisk.socialanalytics.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal,
                            OAuth2AuthenticationToken authentication,
                            Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("name", principal.getAttribute("name") != null
                ? principal.getAttribute("name") : "Unknown");
        // email là null với Twitter — template hiển thị "N/A"
        model.addAttribute("email", principal.getAttribute("email"));
        model.addAttribute("provider", authentication.getAuthorizedClientRegistrationId());
        return "dashboard";
    }
}
