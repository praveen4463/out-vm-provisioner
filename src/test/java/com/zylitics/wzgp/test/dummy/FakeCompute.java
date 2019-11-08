package com.zylitics.wzgp.test.dummy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.springframework.http.HttpStatus;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;

public class FakeCompute {

  private Compute compute;
  
  public FakeCompute() {
    HttpTransport fakeTransport = new HttpTransport() {
      
      @Override
      protected LowLevelHttpRequest buildRequest(String method, String url) {
        return new LowLevelHttpRequest() {
          
          @Override
          public LowLevelHttpResponse execute() {
            return new FakeHttpResponse();
          }
          
          @Override
          public void addHeader(String name, String value) {}
        };
      }
    };
    compute = new Compute.Builder(fakeTransport
        , JacksonFactory.getDefaultInstance()
        , null)
        .setApplicationName("zl-wzgp")
        .build();
  }
  
  public Compute get() {
    return compute;
  }
  
  private static final class FakeHttpResponse extends LowLevelHttpResponse {
    
    @Override
    public InputStream getContent() {
      String json = "{\"name\": \"xxx\"}";
      return new ByteArrayInputStream(json.getBytes());
    }
    
    @Override
    public String getContentEncoding() {
      return "UTF-8";
    }
    
    @Override
    public long getContentLength() {
      return 0;
    }
    
    @Override
    public String getContentType() {
      return "application/json";
    }
    
    @Override
    public int getHeaderCount() {
      return 1;
    }
    
    @Override
    public String getHeaderName(int index) {
      return "authentication";
    }
    
    @Override
    public String getHeaderValue(int index) {
      return "xxx";
    }
    
    @Override
    public String getReasonPhrase() {
      return "sample";
    }
    
    @Override
    public int getStatusCode() {
      return HttpStatus.OK.value();
    }
    
    @Override
    public String getStatusLine() {
      return "200 OK";
    }
  }
}
