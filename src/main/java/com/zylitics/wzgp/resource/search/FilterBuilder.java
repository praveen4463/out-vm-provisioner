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
  
  public FilterBuilder addCondition(String key, String value, Operator operator) {
    Assert.hasText(key, "key can't be empty");
    Assert.notNull(value, "value can't be null");
    
    builder.append(SEARCH_TMPL
        .replace("L", key)
        .replace("O", operator.symbol)
        .replace("R", "\"" + value + "\""));
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