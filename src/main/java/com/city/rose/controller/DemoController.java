package com.city.rose.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/test")
    public String hellorWord(){
        return "Hellor word!";
    }
}
