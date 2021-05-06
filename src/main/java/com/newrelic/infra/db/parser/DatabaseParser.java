package com.newrelic.infra.db.parser;

import com.google.gson.JsonObject;
import com.newrelic.infra.publish.api.metrics.Metric;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The abstract database result parser class.
 */
public abstract class DatabaseParser {
  protected static final Logger logger = LoggerFactory.getLogger(DatabaseParser.class);

  // public abstract List<List<Metric>> parseResultSet(ResultSet rs);

  public abstract String getParserName();

  public abstract List<Metric> parseMetricRow(String metricType, ResultSet resultSet,
      ResultSetMetaData resultSetMetaData) throws Exception;

  public abstract Map<String, String> parseInventoryRow(
      ResultSet resultSet,
      ResultSetMetaData resultSetMetaData
  ) throws Exception;

  public abstract Map<String, Object> parseRawRow(
      ResultSet resultSet,
      ResultSetMetaData resultSetMetaData
  ) throws Exception;

  public void setOptions(JsonObject options) {
    logger.error("Selected DatabaseParser does not support parserOptions, ignoring...");
  }

  public void prepareStatement(PreparedStatement statement) throws Exception {
  }

  public boolean isStateful() {
    return false;
  }

  /*
   * Before / After methods if your parser needs to perform cleanup. Default to
   * nothing.
   */
  public void beforeQuery() throws Exception {
  }

  public void afterQuery() throws Exception {
  }

}