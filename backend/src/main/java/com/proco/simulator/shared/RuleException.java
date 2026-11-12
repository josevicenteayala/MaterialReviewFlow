package com.proco.simulator.shared;
public class RuleException extends RuntimeException {
 public final int status;
 public RuleException(int status,String message){super(message);this.status=status;}
 public static void require(boolean valid,int status,String message){if(!valid)throw new RuleException(status,message);}
}
