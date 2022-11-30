package com.newrelic.infra.db;

import com.newrelic.infra.db.security.EncryptorUtils;
import com.newrelic.infra.publish.api.Agent;
import com.newrelic.infra.publish.api.AgentFactory;
import java.io.IOException;
import java.util.Map;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create the DB agent factory.
 */
public class InfraDbAgentFactory extends AgentFactory {
  private static final Logger logger = LoggerFactory.getLogger(InfraDbAgentFactory.class);

  @Override
  public Agent createAgent(Map<String, Object> properties) throws Exception {

    logger.info("Creating Agent ");

    String name = (String) properties.get("name");
    Integer port = 0;
    if (properties.get("port") != null) {
      port = (Integer) properties.get("port");
    }
    String password = null;
    if (properties.get("password") != null) {
      try {
        password = EncryptorUtils.decrypt((String) properties.get("password"));
      } catch (IOException | EncryptionOperationNotPossibleException e) {
        logger.error("Unable to decrypt password for agent '" + name + "'");
        throw e;
      }
    }

    // SSL Properties
    boolean sslConnection = false;
    if (properties.get("sslConnection") != null) {
      sslConnection = (boolean) properties.get("sslConnection");
    }

    boolean sslEncrypt = false;
    if (properties.get("sslEncrypt") != null) {
      sslEncrypt = (boolean) properties.get("sslEncrypt");
    }

    boolean sslTrustServerCert = false;
    if (properties.get("sslTrustServerCert") != null) {
      sslTrustServerCert = (boolean) properties.get("sslTrustServerCert");
    }

    String sslHostnameInCert = null;
    if (properties.get("sslHostnameInCert") != null) {
      try {
        sslHostnameInCert = (String) properties.get("sslHostnameInCert");
      } catch (Exception e) {
        logger.warn("There is no sslHostnameInCert defined");
      }
    }

    String sslTrustStoreLocation = null;
    if (properties.get("sslTrustStoreLocation") != null) {
      try {
        sslTrustStoreLocation = (String) properties.get("sslTrustStoreLocation");
      } catch (Exception e) {
        logger.warn("There is no sslTrustStoreLocation defined");
      }
    }

    String sslTrustStorePassword = null;
    if (properties.get("sslTrustStorePassword") != null) {
      try {
        sslTrustStorePassword = EncryptorUtils.decrypt(
            (String) properties.get("sslTrustStorePassword")
        );
      } catch (IOException | EncryptionOperationNotPossibleException e) {
        logger.error("Unable to decrypt sslTrustStorePassword for agent '" + name + "'");
        throw e;
      }
    }

    boolean useSsl = false;
    if (properties.get("useSsl") != null) {
      useSsl = (boolean) properties.get("useSsl");
    }

    String inputfile = (String) properties.get("inputfile");
    String hostname = (String) properties.get("host");
    String username = (String) properties.get("username");

    if (
        name == null
        || hostname == null
        || username == null
        || password == null
        || inputfile == null
    ) {
      logger.error("Missing configuration property on input.json, review");
      throw new Exception(
          "'name', 'host', 'username', 'password', and 'inputfile' cannot be null."
      );
    }

    logger.info(
        "Agent: name["
        + name
        + "] hostame["
        + hostname + "] port["
        + port
        + "] username["
        + username
        + "] inputfile["
        + inputfile + "]"
    );

    return new InfraDbAgent(
        name,
        hostname,
        port,
        username,
        password,
        inputfile,
        sslConnection,
        sslEncrypt,
        sslTrustServerCert,
        sslHostnameInCert,
        sslTrustStoreLocation,
        sslTrustStorePassword,
        useSsl
    );
  }
}
