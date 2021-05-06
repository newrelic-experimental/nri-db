package com.newrelic.infra.db.security;

import java.io.Console;
import java.io.IOException;

/**
 * The encryption application entry point.
 */
public class Main {
  /**
   * Program entry.
   */
  public static void main(String[] args) throws Exception {
    String password = null;
    EncryptorUtils.PasswordFileCheckResult result = EncryptorUtils.checkPasswordFile();
    
    if (result == EncryptorUtils.PasswordFileCheckResult.MISSING) {
      System.out.println("The encryption password file does not exist.");
      System.out.println("One will be generated now.");
      
      try {
        EncryptorUtils.generatePasswordFile();
        System.out.println(String.format(
            "A new encryption password file has been created at %s. Please validate that this file is properly secured and re-run this tool.",
            EncryptorUtils.PASSWORD_FILE_PATH.toString()
        ));
      } catch (IOException ioex) {
        System.err.println(String.format(
            "The password file could not be written for the following reason: %s",
            ioex.getMessage()
        ));
        ioex.printStackTrace();
      }
      return;
    } else if (result == EncryptorUtils.PasswordFileCheckResult.INSECURE) {
      System.out.println(String.format(
          "The encryption password file has at %s has POSIX permissions that are too open. Please validate that this file is readable and writeable by the owner only.",
          EncryptorUtils.PASSWORD_FILE_PATH.toString()
      ));
      return;
    }
    
    if (args == null || args.length == 0) {
      Console console = System.console();
      
      if (console == null) {
        System.out.println("Usage: EncryptorUtils <password to encrypt>");
        return;
      }
      
      System.out.println("Enter password:");
      char[] chars = System.console().readPassword();
      
      if (chars == null || chars.length == 0) {
        return;
      }
      
      password = new String(chars);
    } else {
      password = args[0];
    }
    
    System.out.println("Encrypting password...");
    System.out.println(EncryptorUtils.encrypt(password));
  }
}