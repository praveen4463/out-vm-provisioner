package com.zylitics.wzgp;

import java.util.Collections;

import com.google.api.client.json.gson.GsonFactory;
import com.zylitics.wzgp.web.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

@SpringBootApplication
public class Launcher {
  
  public static void main(String[] args) {
    SpringApplication.run(Launcher.class, args);
  }
  
  @Bean
  @Profile({"production", "e2e"})
  public Compute compute() throws Exception {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(
          Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    }
    
    return new Compute.Builder(GoogleNetHttpTransport.newTrustedTransport()
        , GsonFactory.getDefaultInstance()
        , new HttpCredentialsAdapter(credentials))
        .setApplicationName("zl-wzgp")
        .build();
  }
  
  @Bean
  @Profile({"production", "e2e"})
  public GridGenerateHandler.Factory gridGenerateHandlerFactory() {
    return new GridGenerateHandlerImpl.Factory();
  }
  
  @Bean
  @Profile({"production", "e2e"})
  public GridGetRunningHandler.Factory gridGetRunningHandlerFactory() {
    return new GridGetRunningHandlerImpl.Factory();
  }
  
  @Bean
  @Profile({"production", "e2e"})
  public GridStartHandler.Factory gridStartHandlerFactory() {
    return new GridStartHandlerImpl.Factory();
  }
  
  @Bean
  @Profile({"production", "e2e"})
  public GridDeleteHandler.Factory gridDeleteHandlerFactory() {
    return new GridDeleteHandlerImpl.Factory();
  }
}
