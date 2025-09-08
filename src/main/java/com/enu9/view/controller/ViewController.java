package com.enu9.view.controller;

import com.enu9.config.ViewProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Controller
public class ViewController {

    @Resource
    private ViewProperties viewProperties;

    @GetMapping({"/{salt}", "/"})
    public String index(@PathVariable(required = false) String salt, Model model) {
        String configSalt = viewProperties.getSalt();

        // 1. 如果配置里没有 salt，允许直接访问 "/"
        if (configSalt == null || configSalt.isEmpty()) {
            if (salt != null) {
                // 有人还带了路径参数，但配置里没有 -> 拒绝
                throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Salt not configured, path param not allowed");
            }
            return renderIndex(model);
        }

        // 2. 如果配置了 salt
        if (salt == null) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Invalid salt");
        }

        if (!Objects.equals(configSalt, salt)) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Invalid salt");
        }

        return renderIndex(model);
    }

    private String renderIndex(Model model) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        model.addAttribute("defaultStart",
                LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0).format(formatter));
        model.addAttribute("defaultEnd",
                LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).format(formatter));
        return "index";
    }


}
