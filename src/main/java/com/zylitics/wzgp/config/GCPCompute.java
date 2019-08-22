package com.zylitics.wzgp.config;

import java.util.Collections;

import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

@Component
public class GCPCompute {
  
  private static final String APPLICATION_NAME = "zl-wzgp";
  
  private final Compute compute;
  
  /**
   * Since this is Component, a thrown exception will halt container and application startup, thus
   * we don't have to log exceptions.
   * Components are singleton by default, the constructor will run just by container and all
   * threads use the same object.
   * @throws Exception
   */
  public GCPCompute() throws Exception {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(
          Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    }
    
    compute = new Compute.Builder(GoogleNetHttpTransport.newTrustedTransport()
        , JacksonFactory.getDefaultInstance()
        , new HttpCredentialsAdapter(credentials))
        .setApplicationName(APPLICATION_NAME)
        .build();
  }
  
  public Compute getCompute() {
    return compute;
  }
}
