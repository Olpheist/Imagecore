package com.green.imagecore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ImagecoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(ImagecoreApplication.class, args);
	}

}
