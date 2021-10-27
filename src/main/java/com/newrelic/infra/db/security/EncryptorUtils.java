package com.newrelic.infra.db.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
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

  public static final String NRIDB_ENCRYPTION_PASSWORD_ENV_VAR_NAME = "NRIDB_ENCRYPTION_PASSWORD";
  public static final Path ENCRYPTION_PASSWORD_FILE_PATH = FileSystems.getDefault().getPath(
      System.getProperty("user.dir"),
      ".nridbrc"
  );
  private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}\\|;:\'\",<.>/?";
  private static final String ENCRYPTION_PASSWORD_PROPERTY_NAME = "encryptionPassword";
  private static final String ENCRYPTION_ALGORITHM = "PBEWITHMD5ANDDES";
  
  /**
   * Result codes that can be returned from {@link EncryptorUtils#checkEncryptionPasswordFile()}.
   */
  public enum EncryptionPasswordFileCheckResult {
    OK,
    MISSING,
    INSECURE
  }

  private static StandardPBEStringEncryptor getDecryptor() throws IOException {
    String encryptionPassword = getEncryptionPassword();
    SimplePBEConfig config = new SimplePBEConfig();
    config.setAlgorithm(ENCRYPTION_ALGORITHM);
    config.setKeyObtentionIterations(1000);
    config.setPassword(encryptionPassword);

    StandardPBEStringEncryptor encryptor
        = new org.jasypt.encryption.pbe.StandardPBEStringEncryptor();
    encryptor.setConfig(config);
    encryptor.initialize();
    return encryptor;
  }

  public static String encrypt(String clearText) throws IOException {
    return PropertyValueEncryptionUtils.encrypt(clearText, getDecryptor());
  }

  public static String decrypt(String encryptedText) throws IOException {
    return PropertyValueEncryptionUtils.decrypt(encryptedText, getDecryptor());
  }
  
  /**
   * Check if the encryption password file exists and is properly secured.
   *
   * @return a {@link EncryptionPasswordFileCheckResult}.
   */
  public static EncryptionPasswordFileCheckResult checkEncryptionPasswordFile() throws IOException {
    File file = ENCRYPTION_PASSWORD_FILE_PATH.toFile();
    
    if (!file.exists()) {
      return EncryptionPasswordFileCheckResult.MISSING;
    }
    
    PosixFileAttributeView posix = Files.getFileAttributeView(
        ENCRYPTION_PASSWORD_FILE_PATH,
        PosixFileAttributeView.class
    );

    if (posix != null) {
      Set<PosixFilePermission> permissions
          = posix.readAttributes().permissions();

      if (
          permissions.size() != 1
          || !permissions.contains(PosixFilePermission.OWNER_READ)
      ) {
        return EncryptionPasswordFileCheckResult.INSECURE;
      }
    } else {
      if (file.canWrite()) {
        return EncryptionPasswordFileCheckResult.INSECURE;
      }
    }
    
    return EncryptionPasswordFileCheckResult.OK;
  }

  /**
   * Generate a new encryption password.
   */
  public static String generateEncryptionPassword() {
    return RandomStringUtils.random(64, EncryptorUtils.CHARS);
  }
  
  /**
   * Generate an encryption password file.
   *
   * @throws IOException if writing the file fails.
   */
  public static void generateEncryptionPasswordFile() throws IOException {
    String encryptionPassword = EncryptorUtils.generateEncryptionPassword();    
    Properties props = new Properties();
    
    props.setProperty(ENCRYPTION_PASSWORD_PROPERTY_NAME, encryptionPassword);
    props.store(
        new OutputStreamWriter(
            Files.newOutputStream(
                ENCRYPTION_PASSWORD_FILE_PATH,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            ),
            "utf-8"
        ),
        ""
    );
    
    PosixFileAttributeView posix = Files.getFileAttributeView(
        ENCRYPTION_PASSWORD_FILE_PATH,
        PosixFileAttributeView.class
    );

    if (posix != null) {
      Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>();
      
      permissions.add(PosixFilePermission.OWNER_READ);
      posix.setPermissions(permissions);
    } else {
      ENCRYPTION_PASSWORD_FILE_PATH.toFile().setReadOnly();
    }
  }
  
  /**
   * Get the encryption password from the environment or password file.
   *
   * @return the encryption password.
   * @throws IOException if the password file is missing, insecure,
   *         does not contain a valid password, or cannot be read.
   */
  public static String getEncryptionPassword() throws IOException {
    /* 
     * Check to see if the encryption password was provided via an
     * environment variable.
     */
    String encryptionPassword = System.getenv(
        EncryptorUtils.NRIDB_ENCRYPTION_PASSWORD_ENV_VAR_NAME
    );
    
    if (encryptionPassword != null) {
      return encryptionPassword;
    }
    
    /*
     * Get the encryption password from the encryption password file.
     */
    EncryptionPasswordFileCheckResult result = checkEncryptionPasswordFile();
    
    if (result != EncryptionPasswordFileCheckResult.OK) {
      throw new IOException(String.format(
          "The encryption password file at %s is either missing or not properly secured.",
          ENCRYPTION_PASSWORD_FILE_PATH.toString()
      ));
    }

    Properties props = new Properties();
    props.load(
        new InputStreamReader(
            Files.newInputStream(ENCRYPTION_PASSWORD_FILE_PATH, StandardOpenOption.READ),
            "utf-8"
        )
    );

    encryptionPassword = props.getProperty(ENCRYPTION_PASSWORD_PROPERTY_NAME);
    
    if (encryptionPassword == null || encryptionPassword.trim().equalsIgnoreCase("")) {
      throw new IOException(String.format(
          "The encryption password file at %s does not contain a valid password.",
          ENCRYPTION_PASSWORD_FILE_PATH.toString()
      ));
    }
    
    return encryptionPassword;
  }
}