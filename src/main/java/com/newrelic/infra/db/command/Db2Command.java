package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The database command class for DB/2.
 */
public class Db2Command extends DatabaseCommand {

  @Override
  public String getDbDriverName() {
    return "com.ibm.db2.jcc.DB2Driver";
  }

  @Override
  public String getDbType() {
    return "db2";
  }

  @Override
  public Connection getConnection() throws SQLException {
    try {
      Class.forName(getDbDriverName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    String url = "jdbc:db2://" + getHostname();

    if (getPort() != 0) {
      url += ":" + getPort();
    }

    url = url + "/" + getDatabase();

    if (isSslConnection()) {
      url += ":sslConnection=" + isSslConnection();
      String location = getSslTrustStoreLocation();
      String password = getSslTrustStorePassword();

      if (location != null) {
        url += ";sslTrustStoreLocation=" + location;
      }
      if (password != null) {
        url += ";sslTrustStorePassword=" + password;
      }

      url += ";";
    }

    // Set URL for data sources
    return DriverManager.getConnection(url, getUsername(), getPassword());
  }
}
