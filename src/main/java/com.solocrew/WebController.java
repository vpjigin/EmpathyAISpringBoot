package com.solocrew;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
    
    // Handle root path
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
    
    // Handle React routes (excluding API and WebSocket paths)
    @GetMapping(value = {"/dashboard", "/profile", "/settings", "/app/**"})
    public String reactRoutes() {
        return "forward:/index.html";
    }
}
