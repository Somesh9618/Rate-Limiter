package com.example.rate_limiter.Controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
public class Controller {
  
  @RequestMapping ("/")
  public String hello(){
    return "Helo Somesh!";
  }
  @RequestMapping ("/about")
  public String about(){
    return "Rate Limiting project!!";
  }
}
