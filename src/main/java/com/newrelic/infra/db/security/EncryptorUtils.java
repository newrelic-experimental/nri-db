package com.newrelic.infra.db.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimplePBEConfig;
import org.jasypt.properties.PropertyValueEncryptionUtils;

/**
 * The encryption utils.
 */
public class EncryptorUtils {

  public static final Path PASSWORD_FILE_PATH = FileSystems.getDefault().getPath(
      System.getProperty("user.home"),
      ".nridbrc"
  );
  private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}\\|;:\'\",<.>/?";
  private static final String PASSWORD_HASH_PROPERTY_NAME = "passwordHash";
  private static final String ENCRYPTION_ALGORITHM = "PBEWITHMD5ANDDES";
  
  /**
   * Result codes that can be returned from {@link EncryptorUtils#checkPasswordFile()}.
   */
  public enum PasswordFileCheckResult {
    OK,
    MISSING,
    INSECURE
  }

  private static StandardPBEStringEncryptor getDecryptor() throws IOException {
    String passwordHash = getPassword();
    SimplePBEConfig config = new SimplePBEConfig();
    config.setAlgorithm(ENCRYPTION_ALGORITHM);
    config.setKeyObtentionIterations(1000);
    config.setPassword(passwordHash);

    StandardPBEStringEncryptor encryptor
        = new org.jasypt.encryption.pbe.StandardPBEStringEncryptor();
    encryptor.setConfig(config);
    encryptor.initialize();
    return encryptor;
  }

  public static String encrypt(String pass) throws IOException {
    return PropertyValueEncryptionUtils.encrypt(pass, getDecryptor());
  }

  public static String decrypt(String encryptedVal) throws IOException {
    return PropertyValueEncryptionUtils.decrypt(encryptedVal, getDecryptor());
  }
  
  /**
   * Check if the password file exists and is properly secured.
   *
   * @return a {@link PasswordFileCheckResult}.
   */
  public static PasswordFileCheckResult checkPasswordFile() throws IOException {
    File file = PASSWORD_FILE_PATH.toFile();
    
    if (!file.exists()) {
      return PasswordFileCheckResult.MISSING;
    }
    
    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(PASSWORD_FILE_PATH);

    for (PosixFilePermission perm : permissions) {
      if (
          !perm.equals(PosixFilePermission.OWNER_READ)
          && !perm.equals(PosixFilePermission.OWNER_WRITE)
          && !perm.equals(PosixFilePermission.OWNER_EXECUTE)
      ) {
        return PasswordFileCheckResult.INSECURE;
      }
    }
    
    return PasswordFileCheckResult.OK;
  }

  /**
   * Generate a password file.
   *
   * @throws IOException if writing the file fails.
   */
  public static void generatePasswordFile() throws IOException {
    String pwd = RandomStringUtils.random(64, EncryptorUtils.CHARS);    
    Properties props = new Properties();
    
    props.setProperty(PASSWORD_HASH_PROPERTY_NAME, pwd);
    props.store(
        new OutputStreamWriter(
            Files.newOutputStream(
                PASSWORD_FILE_PATH,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            ),
            "utf-8"
        ),
        ""
    );
    
    Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>();
    
    permissions.add(PosixFilePermission.OWNER_READ);
    permissions.add(PosixFilePermission.OWNER_WRITE);
    Files.setPosixFilePermissions(
        PASSWORD_FILE_PATH,
        permissions
    );
  }
  
  /**
   * Get the password from the encryption password file.
   *
   * @return the encryption password.
   * @throws IOException if the password file is missing, insecure,
   *         does not contain a valid password, or cannot be read.
   */
  public static String getPassword() throws IOException {
    PasswordFileCheckResult result = checkPasswordFile();
    
    if (result != PasswordFileCheckResult.OK) {
      throw new IOException(String.format(
          "The encryption password file at %s is either missing or not properly secured.",
          PASSWORD_FILE_PATH.toString()
      ));
    }

    Properties props = new Properties();
    props.load(
        new InputStreamReader(
            Files.newInputStream(PASSWORD_FILE_PATH, StandardOpenOption.READ),
            "utf-8"
        )
    );

    String password = props.getProperty(PASSWORD_HASH_PROPERTY_NAME);
    
    if (password == null || password.trim().equalsIgnoreCase("")) {
      throw new IOException(String.format(
          "The encryption password file at %s does not contain a valid password.",
          PASSWORD_FILE_PATH.toString()
      ));
    }
    
    return password;
  }
}