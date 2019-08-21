package com.zylitics.wzgp.util;

import java.util.Random;

public class Randoms {
  
  private final static String CHAR_SET = "0123456789ABCDEFGHIZKLMNOPQRSTUVWXYZabcdefghizklmnopqrstu"
      + "vwxyz";
  
  private final Random random;
  
  public Randoms(Random random) {
    this.random = random;
  }
  
  public String generateRandom(int length) {
    StringBuilder sb = new StringBuilder(length);
    int charSetLength = CHAR_SET.length();
    for (int i = 0; i < length; i++) {
      sb.append(CHAR_SET.charAt(random.nextInt(charSetLength)));
    }
    return sb.toString();
  }
}
