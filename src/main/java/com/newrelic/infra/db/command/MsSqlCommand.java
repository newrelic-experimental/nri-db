package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The database command class for MSSQL.
 */
public class MsSqlCommand extends DatabaseCommand {

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

    url += ";user=" + getUsername() + ";password=" + getPassword();

    // Set URL for data sources
    return DriverManager.getConnection(url);
  }
}
