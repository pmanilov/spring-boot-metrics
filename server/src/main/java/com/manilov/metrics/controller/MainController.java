package com.manilov.metrics.controller;

import com.manilov.metrics.service.TimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@Controller
public class MainController {

    @Autowired
    private TimeService timeService;
    @PostMapping("/")
    @ResponseStatus(HttpStatus.OK)
    public void handleTime(@RequestParam("time") long clientTime){
        timeService.update(clientTime);
    }
}
