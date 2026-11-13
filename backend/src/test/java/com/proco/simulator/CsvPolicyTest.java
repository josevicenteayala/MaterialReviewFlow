package com.proco.simulator;
import com.proco.simulator.monitoring.CsvPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CsvPolicyTest {
 @Test void spreadsheetValuesAreQuotedAndDangerousLeadingCharactersNeutralized(){
  for(String value:new String[]{"=1+1","+cmd","-1","@SUM(A1)","\t=HYPERLINK(\"x\")","\r\n@SUM(A1)","\uFEFF=1","\u00a0+1","\u0000-1"})assertTrue(CsvPolicy.cell(value).startsWith("\"'"),value);
  assertEquals("\"a\"\"b,c  next\"",CsvPolicy.cell("a\"b,c\r\nnext"));assertEquals("\"área GEO\"",CsvPolicy.cell("área GEO"));assertEquals("\"\"",CsvPolicy.cell(null));
 }
}
