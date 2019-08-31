package com.zylitics.wzgp;

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.zylitics.wzgp.config.APICorePropertiesImpl;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.executor.ResourceExecutorImpl;
import com.zylitics.wzgp.web.GridDeleteHandler;
import com.zylitics.wzgp.web.GridDeleteHandlerImpl;
import com.zylitics.wzgp.web.GridGenerateHandler;
import com.zylitics.wzgp.web.GridGenerateHandlerImpl;
import com.zylitics.wzgp.web.GridStartHandler;
import com.zylitics.wzgp.web.GridStartHandlerImpl;

@SpringBootApplication
public class Launcher {
  
  public static void main(String[] args) {
    SpringApplication.run(Launcher.class, args);
  }
  
  @Bean
  public Compute getCompute() throws Exception {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(
          Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    }
    
    return new Compute.Builder(GoogleNetHttpTransport.newTrustedTransport()
        , JacksonFactory.getDefaultInstance()
        , new HttpCredentialsAdapter(credentials))
        .setApplicationName("zl-wzgp")
        .build();
  }
  
  @Bean
  public APICoreProperties getAPICoreProperties() {
    return new APICorePropertiesImpl();
  }
  
  @Bean
  public ResourceExecutor getResourceExecutor() throws Exception {
    return new ResourceExecutorImpl(getCompute(), getAPICoreProperties());
  }
  
  @Bean
  public GridGenerateHandler.Factory getGridGenerateHandlerFactory() {
    return new GridGenerateHandlerImpl.Factory();
  }
  
  @Bean
  public GridStartHandler.Factory getGridStartHandlerFactory() {
    return new GridStartHandlerImpl.Factory();
  }
  
  @Bean
  public GridDeleteHandler.Factory getGridDeleteHandlerFactory() {
    return new GridDeleteHandlerImpl.Factory();
  }
}
