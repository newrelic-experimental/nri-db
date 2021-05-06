package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The database command class for MySQL.
 */
public class MySqlCommand extends DatabaseCommand {

  @Override
  public String getDbType() {
    return "MySQL";
  }

  @Override
  public String getDbDriverName() {
    return "com.mysql.cj.jdbc.Driver";
  }

  @Override
  public Connection getConnection() throws SQLException {

    try {
      Class.forName(getDbDriverName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    String url = "jdbc:mysql://" + getHostname();

    if (getPort() != 0) {
      url += ":" + getPort();
    }

    if (!getDatabase().isEmpty() && !getDatabase().equalsIgnoreCase(defaultDb)) {
      url += "/" + getDatabase();
    }

    Properties props = new Properties();
    props.setProperty("user", getUsername());
    props.setProperty("password", getPassword());

    return DriverManager.getConnection(url, props);
  }
}
