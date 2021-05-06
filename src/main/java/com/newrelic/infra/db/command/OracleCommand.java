package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The database command class for Oracle.
 */
public class OracleCommand extends DatabaseCommand {

  @Override
  public String getDbDriverName() {
    return "oracle.jdbc.driver.OracleDriver";
  }

  @Override
  public String getDbType() {
    return "Oracle";
  }

  @Override
  public Connection getConnection() throws SQLException {

    try {
      Class.forName(getDbDriverName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    String url = "jdbc:oracle:thin:@//" + getHostname();

    if (getPort() != 0) {
      url += ":" + getPort();
    }

    url += "/" + getDatabase();

    // Set URL for data sources
    return DriverManager.getConnection(url, getUsername(), getPassword());
  }
}
