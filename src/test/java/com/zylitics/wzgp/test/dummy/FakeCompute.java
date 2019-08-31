package com.zylitics.wzgp.test.dummy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
      protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        return new LowLevelHttpRequest() {
          
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            return new FakeHttpResponse();
          }
          
          @Override
          public void addHeader(String name, String value) throws IOException {}
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
    public InputStream getContent() throws IOException {
      String json = "{\"name\": \"xxx\"}";
      return new ByteArrayInputStream(json.getBytes());
    }
    
    @Override
    public String getContentEncoding() throws IOException {
      return "UTF-8";
    }
    
    @Override
    public long getContentLength() throws IOException {
      return 0;
    }
    
    @Override
    public String getContentType() throws IOException {
      return "application/json";
    }
    
    @Override
    public int getHeaderCount() throws IOException {
      return 1;
    }
    
    @Override
    public String getHeaderName(int index) throws IOException {
      return "authentication";
    }
    
    @Override
    public String getHeaderValue(int index) throws IOException {
      return "xxx";
    }
    
    @Override
    public String getReasonPhrase() throws IOException {
      return "sample";
    }
    
    @Override
    public int getStatusCode() throws IOException {
      return HttpStatus.OK.value();
    }
    
    @Override
    public String getStatusLine() throws IOException {
      return "200 OK";
    }
  }
}
