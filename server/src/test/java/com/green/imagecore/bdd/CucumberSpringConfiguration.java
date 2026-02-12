package com.green.imagecore.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import com.green.imagecore.ImagecoreApplication;

@CucumberContextConfiguration
@SpringBootTest(classes = ImagecoreApplication.class)
public class CucumberSpringConfiguration {
}

