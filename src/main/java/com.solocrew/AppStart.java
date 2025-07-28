package com.solocrew;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan(basePackages = "com.solocrew")
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AppStart extends SpringBootServletInitializer implements ApplicationRunner {

	public static void main(String[] args) {
    	SpringApplication.run(AppStart.class, args);
	}

	@Override
	public void run(ApplicationArguments args) {
	}

}
