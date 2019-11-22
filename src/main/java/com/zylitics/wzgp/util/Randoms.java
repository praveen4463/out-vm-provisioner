package com.zylitics.wzgp.util;

import java.util.Random;

public class Randoms {
  
  private final String charSet;
  
  private final Random random;
  
  public Randoms(String charSet, Random random) {
    this.charSet = charSet;
    this.random = random;
  }
  
  public Randoms(String charSet) {
    this(charSet, new Random());
  }
  
  public String generateRandom(int length) {
    StringBuilder sb = new StringBuilder(length);
    int charSetLength = charSet.length();
    for (int i = 0; i < length; i++) {
      sb.append(charSet.charAt(random.nextInt(charSetLength)));
    }
    return sb.toString();
  }
}
