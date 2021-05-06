package com.newrelic.infra.db.command;

import java.nio.BufferOverflowException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * The database command class for HSQLDB.
 */
public class HsqlDbCommand extends DatabaseCommand {

  @Override
  public String getDbDriverName() {
    return "org.hsqldb.jdbc.JDBCDriver";
  }

  @Override
  public String getDbType() {
    return "HSQLDB";
  }

  @Override
  public Connection getConnection() throws SQLException {
    try {
      Class.forName(getDbDriverName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    String url = "jdbc:" + getDbType().toLowerCase(Locale.ENGLISH) + ":mem:" + getDatabase();

    // Set URL for data sources
    return DriverManager.getConnection(url, getUsername(), getPassword());
  }

  /**
   * calculateHash generates a hash of the entire row in an attempt to deduplicate
   * data HSQLDB does not support getBytes for non-binary types, so we have to
   * override the way we retrieve all the columns per row.
   *
   * @param resultSet         Data to read from, should point at the requested row
   *                          to hash
   * @param resultSetMetaData Needed to know how wide the row is, data about the
   *                          columns
   * @return String containing hash calculated for the row
   */
  @Override
  public String calculateHash(ResultSet resultSet, ResultSetMetaData resultSetMetaData) {
    String rowHash = null;

    // Grab all the bytes from the row, checksum it, and return if we had a
    // duplicate
    try {
      final StringBuilder rawRow = new StringBuilder("");

      for (int c = 1; c < resultSetMetaData.getColumnCount(); c++) {
        Object x = resultSet.getObject(c);

        rawRow.append(x);
      }

      rowHash = DigestUtils.md5Hex(rawRow.toString().getBytes(StandardCharsets.UTF_8));
    } catch (SQLException | BufferOverflowException | NullPointerException e) {
      logger.error("Unable to check row for duplicate", e);
      return null;
    }

    return rowHash;
  }
}
