package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The database command class for MSSQL.
 */
public class MsSqlCommand extends DatabaseCommand {
  private static final Logger logger = LoggerFactory.getLogger(MsSqlCommand.class);

  @Override
  public String getDbType() {
    return "MSSQL";
  }

  @Override
  public String getDbDriverName() {
    return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
  }

  @Override
  public Connection getConnection() throws SQLException {

    try {
      Class.forName(getDbDriverName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    String url = "jdbc:sqlserver://" + getHostname();

    if (getPort() != 0) {
      url += ":" + getPort();
    }
    url += ";database=" + getDatabase();

    url += ";user=" + getUsername() + ";password=" + getPassword();

    if (isSslConnection()) {
      String location = getSslTrustStoreLocation();
      String password = getSslTrustStorePassword();
      String hostnameCert = getSslHostnameInCert();

      if (location != null) {
        url += ";trustStore=" + location;
      }
      if (password != null) {
        url += ";trustStorePassword=" + password;
      }
      if (hostnameCert != null) {
        url += ";hostNameInCertificate=" + hostnameCert;
      }

      if (isSslEncrypt()) {
        url += ";encrypt=true";
      } else {
        url += ";encrypt=false";
      }

      if (isSslTrustServerCert()) {
        url += ";trustServerCertificate=true";
      } else {
        url += ";trustServerCertificate=false";
      }
    }

    // Set URL for data sources
    return DriverManager.getConnection(url);
  }
}
