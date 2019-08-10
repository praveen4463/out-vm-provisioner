package com.zylitics.wzgp.service;

public final class GCPFilterBuilder {
  
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
  
  public <T> GCPFilterBuilder addCondition(String key
      , T value
      , Class<T> valueClazz
      , Operator operator) {
    
    String formattedValue = (String) value;
    if (valueClazz.getClass().getName().equals(String.class.getName())) {
      formattedValue = "\"" + formattedValue + "\""; 
    }
    builder.append(SEARCH_TMPL
        .replace("L", key)
        .replace("O", operator.symbol)
        .replace("R", formattedValue));
    builder.append(" "); // white space after each expression
    return this;
  }
  
  public GCPFilterBuilder addConditionalExpr(ConditionalExpr conditionalExpr) {
    builder.append(conditionalExpr.name());
    builder.append(" ");
    return this;
  }
  
  public String build() {
    return builder.toString();
  }
}