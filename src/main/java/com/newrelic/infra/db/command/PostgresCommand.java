package com.newrelic.infra.db.command;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The database command class for Postgres.
 */
public class PostgresCommand extends DatabaseCommand {

  @Override
  public String getDbType() {
    return "Postgres";
  }

  @Override
  public String getDbDriverName() {
    return "org.postgresql.Driver";
  }

  @Override
  public Connection getConnection() throws SQLException {

    try {
      Class.forName(getDbDriverName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    String url = "jdbc:postgresql://" + getHostname();

    if (getPort() != 0) {
      url += ":" + getPort();
    }

    url += "/" + getDatabase();

    Properties props = new Properties();
    props.setProperty("user", getUsername());
    props.setProperty("password", getPassword());
    // props.setProperty("ssl","false");

    return DriverManager.getConnection(url, props);
  }
}
