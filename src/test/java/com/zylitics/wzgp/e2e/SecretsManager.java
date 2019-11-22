package com.zylitics.wzgp.e2e;

import com.zylitics.wzgp.support.CloudKMSSecretsManager;

import java.io.IOException;

public interface SecretsManager extends AutoCloseable {
  
  String getSecretAsPlainText(String secretCloudFileName);
  
  interface Factory {
    
    static Factory getDefault() {
      return new CloudKMSSecretsManager.Factory();
    }
    
    SecretsManager create() throws IOException;
  }
}
