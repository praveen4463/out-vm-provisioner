package com.zylitics.wzgp.config;

import java.io.IOException;
import java.util.Collections;

import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

@Component
public class GCPCompute {
  
  private static final String APPLICATION_NAME = "zl-wzgp";
  
  private final Compute compute;
  private final AccessToken token;
  
  /**
   * Since this is Component, a thrown exception will halt container and application startup, thus
   * we don't have to log exceptions.
   * Components are singleton by default, the constructor will run just by container and all
   * threads use the same object.
   * @throws Exception
   */
  public GCPCompute() throws Exception {
    compute = new Compute.Builder(GoogleNetHttpTransport.newTrustedTransport()
        , JacksonFactory.getDefaultInstance()
        , null)
        .setApplicationName(APPLICATION_NAME)
        .build();
    
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(
          Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    }
    token = getCredentials(credentials);
  }
  
  private AccessToken getCredentials(GoogleCredentials credentials) throws IOException {
    AccessToken token = credentials.getAccessToken();
    if (token == null) {
      credentials.refresh();
      token = credentials.getAccessToken();
    }
    return token; 
  }
  
  public Compute getCompute() {
    return compute;
  }
  
  public AccessToken getToken() {
    return token;
  }
}
