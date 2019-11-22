package com.zylitics.wzgp.support;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.zylitics.wzgp.e2e.SecretsManager;

import java.io.IOException;

public class CloudKMSSecretsManager implements SecretsManager {
  
  private static final String PROJECT = "zl-win-nodes";
  
  private static final String KEY_RING = "zl-apps-auth-secret";
  
  private static final String KEY = "default-prod";
  
  private static final String BUCKET = "zl-secrets";
  
  /* client should be created just once for all usages as it takes ~1000ms average time, and closed
   * , once all done.
   */
  private final KeyManagementServiceClient client;
  /* ~1400ms if it's the first time (class loading into JVM), ~300ms afterwards */
  private final Storage storage;
  
  private CloudKMSSecretsManager() throws IOException {
    client = KeyManagementServiceClient.create();
    storage = StorageOptions.getDefaultInstance().getService();
  }
  
  CloudKMSSecretsManager(KeyManagementServiceClient client, Storage storage) {
    this.client = client;
    this.storage = storage;
  }
  
  @Override
  public String getSecretAsPlainText(String secretCloudFileName) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(secretCloudFileName),
        "secret cloud file name can't be empty");
    
    BlobId blobId = BlobId.of(BUCKET, secretCloudFileName);
    // we'll throw if there is any error for now as storage and kms both have retry built-in and
    // i don't expect storage to throw any error that needs retry from user while 'getting' blob.
    byte[] content = storage.readAllBytes(blobId);
    String resourceName = CryptoKeyName.format(PROJECT, "global", KEY_RING, KEY);
    DecryptResponse decrypt = client.decrypt(resourceName, ByteString.copyFrom(content));
    // trim is important to remove unintended whitespaces.
    return decrypt.getPlaintext().toStringUtf8().trim();
  }
  
  @Override
  public void close() {
    client.close();
  }
  
  public static class Factory implements SecretsManager.Factory {
    
    @Override
    public SecretsManager create() throws IOException {
      return new CloudKMSSecretsManager();
    }
  }
}
