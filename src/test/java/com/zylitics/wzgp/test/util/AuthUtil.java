package com.zylitics.wzgp.test.util;

import java.util.Base64;

public class AuthUtil {
  
  public static final String AUTHORIZATION = "Authorization";
  
  public static String getBasicAuthHeaderValue(String user, String pwd) {
    return Base64.getEncoder().encodeToString((user + ":" + pwd).getBytes());
  }
}

