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
    if (args != null && args.length == 1 && args[0].equalsIgnoreCase("--generate")) {
      System.out.println(EncryptorUtils.generateEncryptionPassword());
      return;
    }
    
    String password = null;
    String encryptionPassword = System.getenv(
        EncryptorUtils.NRIDB_ENCRYPTION_PASSWORD_ENV_VAR_NAME
    );
    
    if (encryptionPassword == null) {
      EncryptorUtils.EncryptionPasswordFileCheckResult result
          = EncryptorUtils.checkEncryptionPasswordFile();
      
      if (result == EncryptorUtils.EncryptionPasswordFileCheckResult.MISSING) {
        System.out.println("The encryption password file does not exist.");
        System.out.println("One will be generated now.");
        
        try {
          EncryptorUtils.generateEncryptionPasswordFile();
          System.out.println(String.format(
              "A new encryption password file has been created at %s. Please validate that this file is properly secured and re-run this tool.",
              EncryptorUtils.ENCRYPTION_PASSWORD_FILE_PATH.toString()
          ));
        } catch (IOException ioex) {
          System.err.println(String.format(
              "The encryption password file could not be written for the following reason: %s",
              ioex.getMessage()
          ));
          ioex.printStackTrace();
        }
        return;
      } else if (result == EncryptorUtils.EncryptionPasswordFileCheckResult.INSECURE) {
        System.out.println(String.format(
            "The encryption password file at %s has POSIX permissions that are too open. Please validate that this file is readable by the owner only.",
            EncryptorUtils.ENCRYPTION_PASSWORD_FILE_PATH.toString()
        ));
        return;
      }
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