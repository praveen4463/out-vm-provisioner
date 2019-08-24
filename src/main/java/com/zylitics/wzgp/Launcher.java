package com.zylitics.wzgp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.zylitics.wzgp.config.APICorePropertiesImpl;
import com.zylitics.wzgp.resource.APICoreProperties;

@SpringBootApplication
public class Launcher {
  
  public static void main(String[] args) {
    SpringApplication.run(Launcher.class, args);
  }
  
  @Bean
  public APICoreProperties getAPICoreProperties() {
    return new APICorePropertiesImpl();
  }
}
