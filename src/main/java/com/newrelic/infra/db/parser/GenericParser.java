package com.newrelic.infra.db.parser;

import static com.newrelic.infra.db.Util.getNumber;
import static com.newrelic.infra.db.Util.getSqlColumnByType;

import com.newrelic.infra.db.command.DatabaseCommand;
import com.newrelic.infra.publish.api.metrics.AttributeMetric;
import com.newrelic.infra.publish.api.metrics.DeltaMetric;
import com.newrelic.infra.publish.api.metrics.GaugeMetric;
import com.newrelic.infra.publish.api.metrics.Metric;
import com.newrelic.infra.publish.api.metrics.RateMetric;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The generic database result parser class.
 */
public class GenericParser extends DatabaseParser {

  @Override
  public String getParserName() {
    return "GenericParser";
  }

  @Deprecated
  public List<Metric> parseMetricRow(
      DatabaseCommand databaseCommand,
      ResultSet rs,
      ResultSetMetaData rsmd
  ) throws Exception {
    return parseMetricRow(databaseCommand.getMetricType(), rs, rsmd);
  }

  @Override
  public List<Metric> parseMetricRow(
      String metricType,
      ResultSet rs,
      ResultSetMetaData rsmd
  ) throws Exception {
    List<Metric> theList = new ArrayList<Metric>();

    Metric metric = null;

    try {
      int numColumns = rsmd.getColumnCount();

      for (int i = 1; i < numColumns + 1; i++) {
        String columnName = rsmd.getColumnName(i).trim().toLowerCase(Locale.ENGLISH);
        Object value = getSqlColumnByType(rs, rsmd, i);

        if (value != null) {
          if (value instanceof String) {
            metric = new AttributeMetric(columnName.trim(), ((String) value).trim());
          } else {
            if (metricType != null) {
              switch (metricType.toLowerCase(Locale.ENGLISH)) {
                case "gauge":
                  metric = new GaugeMetric(columnName, getNumber(value));
                  break;
                case "delta":
                  metric = new DeltaMetric(columnName, getNumber(value));
                  break;
                case "rate":
                  metric = new RateMetric(columnName, getNumber(value));
                  break;
                default:
                  logger.error("Unknown Metric Type: '" + metricType + "'");
                  continue;
              }
            } else {
              // Default to Gauge
              metric = new GaugeMetric(columnName, getNumber(value));
            }
          }
          theList.add(metric);
        }
      }
      logger.debug("returning metricList: [" + theList + "]");
    } catch (SQLException e) {
      logger.error("Error parsing Metric row: [" + rs.getRow() + "]", e);
      throw e;
    }

    return theList;
  }

  @Deprecated
  public Map<String, String> parseInventoryRow(
      DatabaseCommand databaseCommand,
      ResultSet rs,
      ResultSetMetaData rsmd
  )
      throws Exception {
    return parseInventoryRow(rs, rsmd);
  }

  @Override
  public Map<String, String> parseInventoryRow(
      ResultSet rs,
      ResultSetMetaData rsmd
  ) throws Exception {
    Map<String, String> row = new HashMap<String, String>();

    try {
      int numColumns = rsmd.getColumnCount();

      for (int i = 1; i < numColumns + 1; i++) {
        String columnName = rsmd.getColumnName(i).trim().toLowerCase(Locale.ENGLISH);
        String value = null;

        if (rs.getString(columnName) != null) {
          value = rs.getString(columnName);
        }

        if (value != null) {
          row.put(columnName, value);
        }
      }

    } catch (SQLException e) {
      logger.error("Error parsing Inventory row: [" + rs.getRow() + "]", e);
      throw e;
    }

    return row;
  }

  /**
   * Parse a row into a map of native Java objects.
   *
   * @param resultSet         Result Set containing this row to process
   * @param resultSetMetaData Metadata about the result set (so we can get the
   *                          name / count of the columns)
   * @return Hash Map of column name to Object
   */
  @Override
  public Map<String, Object> parseRawRow(
      ResultSet resultSet,
      ResultSetMetaData resultSetMetaData
  ) throws Exception {
    Map<String, Object> row = new HashMap<>();
    int currentRow = 0;

    try {
      int numColumns = resultSetMetaData.getColumnCount();
      currentRow = resultSet.getRow();

      for (int i = 1; i < numColumns + 1; i++) {
        String columnName = resultSetMetaData.getColumnName(i).trim().toLowerCase(Locale.ENGLISH);
        Object value = getSqlColumnByType(resultSet, resultSetMetaData, i);

        if ((!columnName.isEmpty()) && (value != null)) {
          row.put(columnName, value);
        }
      }
    } catch (SQLException e) {
      logger.error("Error parsing row: [" + currentRow + "]", e);
      throw e;
    }

    return row;
  }

}
