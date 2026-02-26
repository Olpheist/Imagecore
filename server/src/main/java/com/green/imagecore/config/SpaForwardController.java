package com.green.imagecore.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    // match anything without a dot, but exclude api/swagger/v3/actuator/webjars explicitly
    @GetMapping({
            "/{path:^(?!api|swagger-ui|v3|actuator|webjars$)[^\\.]*$}",
            "/**/{path:^(?!api|swagger-ui|v3|actuator|webjars$)[^\\.]*$}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}