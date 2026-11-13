package com.proco.simulator.monitoring;

/** Quoted UTF-8 CSV. JSON exports retain original strings; spreadsheet-facing CSV neutralizes them. */
public final class CsvPolicy {
 private CsvPolicy() {}
 public static String cell(Object value){
  String raw=value==null?"":value.toString();var clean=new StringBuilder();
  raw.codePoints().forEach(c->clean.appendCodePoint(Character.isISOControl(c)||Character.getType(c)==Character.FORMAT?' ':c));
  String safe=clean.toString();int start=0;
  while(start<safe.length()){int c=safe.codePointAt(start);if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c))break;start+=Character.charCount(c);}
  String leading=safe.substring(start);
  if(!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0))>=0)safe="'"+safe;
  return "\""+safe.replace("\"","\"\"")+"\"";
 }
}
