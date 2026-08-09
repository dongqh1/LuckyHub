package com.dongqh.luckyhub.integration.gateway;
final class GatewayValidation {
 private GatewayValidation() {}
 static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+"不能为空");return value.trim();}
 static Long positive(Long value,String field){if(value==null||value<=0)throw new IllegalArgumentException(field+"必须大于0");return value;}
 static int positive(int value,String field){if(value<=0)throw new IllegalArgumentException(field+"必须大于0");return value;}
 static long positive(long value,String field){if(value<=0)throw new IllegalArgumentException(field+"必须大于0");return value;}
 static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
 static String bounded(String value,int max){String normalized=optional(value);return normalized==null?null:normalized.substring(0,Math.min(normalized.length(),max));}
}
