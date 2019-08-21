package com.zylitics.wzgp.resource.search;

import org.springframework.util.Assert;

/**
 * Builds filter per GCP compute guidelines.
 * @author Praveen Tiwari
 *
 */
public final class FilterBuilder {
  
  private static final String SEARCH_TMPL = "(L O R)";
  
  enum Operator {
    EQ ("="),
    NOT_EQ ("!="),
    GTR (">"),
    LES ("<");
    
    private final String symbol;
    
    private Operator(String symbol) {
      this.symbol = symbol;
    }
  }
  
  enum ConditionalExpr {
    AND,
    OR
  }
  
  private StringBuilder builder = new StringBuilder();
  
  public <T> FilterBuilder addCondition(String key
      , T value
      , Class<T> valueClazz
      , Operator operator) {
    Assert.hasText(key, "key can't be empty");
    Assert.notNull(value, "value can't be null");
    
    String formattedValue = (String) value;
    if (valueClazz.getClass().getName().equals(String.class.getName())) {
      formattedValue = "\"" + formattedValue + "\""; 
    }
    builder.append(SEARCH_TMPL
        .replace("L", key)
        .replace("O", operator.symbol)
        .replace("R", formattedValue));
    return this;
  }
  
  public FilterBuilder addConditionalExpr(ConditionalExpr conditionalExpr) {
    builder.append(" " + conditionalExpr.name() + " ");
    return this;
  }
  
  public String build() {
    return builder.toString();
  }
}