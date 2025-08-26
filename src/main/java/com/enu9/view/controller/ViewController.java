package com.enu9.view.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class ViewController {
    @GetMapping({"/", "/ui"})
    public String index(Model model) {
        // 默认时间：最近 7 天
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        model.addAttribute("defaultStart", LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0).format(formatter));
        model.addAttribute("defaultEnd", LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).format(formatter));
        return "index";
    }
}
