package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The database command class for Sybase.
 */
public class SybaseCommand extends DatabaseCommand {

  @Override
  public String getDbType() {
    return "Sybase";
  }

  @Override
  public String getDbDriverName() {
    return "com.sybase.jdbc42.jdbc.SybDriver";
  }

  @Override
  public Connection getConnection() throws SQLException {
    if (useSsl()) { // encrypt password in transit with bouncy castle
      String url = "jdbc:sybase:Tds:" + getHostname() + ":" + getPort() + "/" + getDatabase();
      logger.info("Bouncy Castle requested. Connection string = " + url);
      Properties props = new Properties();
      props.put("ENCRYPT_PASSWORD", "true");
      props.put("JCE_PROVIDER_CLASS", "org.bouncycastle.jce.provider.BouncyCastleProvider");
      props.put("user", getUsername());
      props.put("password", getPassword());
      return DriverManager.getConnection(url, props);
    } else { // no encryption
      String url = "jdbc:sybase:Tds:" + getHostname() + ":" + getPort() + "/" + getDatabase();
      return DriverManager.getConnection(url, getUsername(), getPassword());
    }
  }
}