package com.newrelic.infra.db.security;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test for EncryptorUtils.
 */
public class EncryptorUtilsTest {

  private static String decryptedPassword = "password";
  private static String encryptedPassword = "ENC(eyNvYJitpB7DZ7vP1177LIAHmkNb6DSb)";

  @Test
  public void testEncrypt() {
    /*
     * TODO: Update this
     * String pass = EncryptorUtils.encrypt(decryptedPassword);
     * System.out.println(encryptedPassword + " " + decryptedPassword + " E " + pass);
     * assertEquals(EncryptorUtils.decrypt(pass), decryptedPassword);
     */
  }

  @Test
  public void testDecrypt() {
    /*
     * TODO: Update this
     * String pass = EncryptorUtils.decrypt(encryptedPassword);
     * System.out.println(decryptedPassword + " " + encryptedPassword + " D " +
     * pass); assertEquals(decryptedPassword, pass);
     */
  }
}