package com.newrelic.infra.db;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static helper methods.
 */
public final class Util {
  private static final Logger logger = LoggerFactory.getLogger(Util.class);

  /**
   * Prevent the class from being constructed.
   */
  private Util() {
  }

  /**
   * Cast an object to a Number.
   *
   * @param value Object to be cast
   * @return Number from Object, or -1 on failure
   */
  public static Number getNumber(Object value) {
    if (value instanceof Integer) {
      return (Integer) value;
    } else if (value instanceof BigInteger) {
      return (BigInteger) value;
    } else if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    } else if (value instanceof Float) {
      return (Float) value;
    } else if (value instanceof Double) {
      return (Double) value;
    } else if (value instanceof Long) {
      return (Long) value;
    }

    logger.warn("getNumber, value not casted[" + value + "], returning -1");
    return -1;
  }

  /**
   * Return a SQL Column as the correct Object type.
   *
   * @param rs     Result Set to get the column from
   * @param rsmd   Metadata to determine what Type the column is
   * @param column Column index to return
   * @return Java Object of the correct Type for the SQL column, null if failed
   * @throws SQLException If there are any SQL issues, an exception is logged and
   *                      thrown.
   */
  public static Object getSqlColumnByType(
      ResultSet rs,
      ResultSetMetaData rsmd,
      int column
  ) throws SQLException {
    Object value = null;

    try {
      switch (rsmd.getColumnType(column)) {
        case java.sql.Types.BIGINT:
          value = rs.getLong(column);
          break;
        case java.sql.Types.BIT:
        case java.sql.Types.BOOLEAN:
          value = Boolean.toString(rs.getBoolean(column));
          break;
        case java.sql.Types.DOUBLE:
          value = rs.getDouble(column);
          break;
        case java.sql.Types.FLOAT:
          value = rs.getFloat(column);
          break;
        case java.sql.Types.INTEGER:
        case java.sql.Types.TINYINT:
        case java.sql.Types.SMALLINT:
          value = rs.getInt(column);
          break;
        case java.sql.Types.DECIMAL:
        case java.sql.Types.NUMERIC:
          if (rs.getBigDecimal(column) != null) {
            value = rs.getBigDecimal(column).intValue();
          }
          break;
        case java.sql.Types.CLOB:
          Clob clob = rs.getClob(column);
          if (clob != null) {
            value = clob.getSubString(1, (int) clob.length());
          }
          break;
        case java.sql.Types.NVARCHAR:
          if (rs.getString(column) != null) {
            value = rs.getNString(column).trim();
          }
          break;
        case java.sql.Types.VARBINARY:
          value = rs.getBinaryStream(column).toString();
          break;
        case java.sql.Types.VARCHAR:
          if (rs.getString(column) != null) {
            value = rs.getString(column).trim();
          }
          break;
        case java.sql.Types.CHAR:
          value = rs.getString(column);
          break;
        case java.sql.Types.DATE:
          // Convert Date to String because the current RPC does not support time/date
          value = rs.getDate(column).toString();
          break;
        case java.sql.Types.TIMESTAMP:
          // Convert Timestamp to String because the current RPC does not support
          // time/date
          if (rs.getTimestamp(column) != null) {
            value = (rs.getTimestamp(column)).toString();
          }
          break;
        default:
          logger.error("Unknown data type: [" + rsmd.getColumnTypeName(column) + "] for column ["
              + rsmd.getColumnName(column) + "]");
          break;
      }
    } catch (SQLException e) {
      throw e;
    }

    return value;
  }
}
