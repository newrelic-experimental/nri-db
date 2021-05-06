package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The database command class for AS/400.
 */
public class As400Command extends DatabaseCommand {

  @Override
  public String getDbDriverName() {
    return "com.ibm.as400.access.AS400JDBCDriver";
  }

  @Override
  public String getDbType() {
    return "as400";
  }

  @Override
  public Connection getConnection() throws SQLException {
    try {
      Class.forName(getDbDriverName());
    } catch (ClassNotFoundException e) {
      logger.error("Unable to find class: '" + getDbDriverName() + "'");
      e.printStackTrace();
    }

    String url = "jdbc:as400://" + getHostname();

    if (getPort() != 0) {
      url += ":" + getPort();
    }

    url += "/" + getDatabase();

    // Set URL for data sources
    return DriverManager.getConnection(url, getUsername(), getPassword());
  }
}
