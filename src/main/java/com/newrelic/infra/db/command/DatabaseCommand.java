package com.newrelic.infra.db.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.infra.db.parser.DatabaseParser;
import com.newrelic.infra.db.parser.GenericParser;
import com.newrelic.infra.publish.api.metrics.AttributeMetric;
import com.newrelic.infra.publish.api.metrics.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The abstract database command class.
 */
public abstract class DatabaseCommand {
  protected static final Logger logger = LoggerFactory.getLogger(DatabaseCommand.class);

  /**
   * The different query types.
   */
  protected enum DbQueryType {
    METRIC, INVENTORY, RAW
  }

  protected static final String defaultParser = "GenericParser";
  protected static final String defaultDb = "UnknownDatabase";
  protected static final String defaultTable = "Unknown Table";
  protected static final int defaultRowBufferSize = 5120;
  protected static final int defaultUniqueHistorySize = 1000;

  public abstract String getDbDriverName();

  public abstract String getDbType();

  public abstract Connection getConnection() throws SQLException;

  private String hostname;
  private int port;
  private String database;
  private String tableName;

  private String username;
  private String password;

  private boolean sslConnection = false;
  private boolean sslEncrypt = false;
  private boolean sslTrustServerCert = false;
  private String sslHostnameInCert = null;
  private String sslTrustStoreLocation = null;
  private String sslTrustStorePassword = null;
  private String version = null;

  private String name;
  private String provider;
  private String query;
  private List<String> queryParameterColumns;
  private List<Object> queryParameterValues;
  private String initialQuery;
  private boolean runInitialQuery;

  protected int uniqueHistorySize;
  protected int rowBufferSize;
  protected boolean deduplicate;
  protected final LinkedHashMap<String, Integer> uniqueHistoryList;

  private String dataType;
  private String metricType;
  private String eventType;
  private List<Metric> defaultMetrics;

  private String prefix;
  private String category;
  private String eventText;

  private DatabaseParser parser;
  private JsonObject parserOptions;

  /**
   * Constructor for DatabaseCommand.
   */
  public DatabaseCommand() {
    setParser(null);
    setProvider(this.getDbType());
    this.queryParameterColumns = new LinkedList<>();
    this.queryParameterValues = new LinkedList<>();
    this.defaultMetrics = new ArrayList<>();

    // Deduplication config
    this.deduplicate = false;
    this.uniqueHistorySize = defaultUniqueHistorySize;
    this.rowBufferSize = defaultRowBufferSize;

    // Using a LinkedHashMap as a cache, it will auto-prune at the uniqueHistorySize
    // limit.
    this.uniqueHistoryList = new LinkedHashMap<String, Integer>() {
      @Override
      protected boolean removeEldestEntry(final Map.Entry eldest) {
        return size() > uniqueHistorySize;
      }
    };
  }

  /**
   * Get metrics.
   */
  public List<List<Metric>> executeMetric() {
    logger.debug("executeMetric, query[" + getQuery() + "]");

    DatabaseResult result = executeQuery(DbQueryType.METRIC, getQuery());

    return result.getMetricResult();
  }

  /**
   * Get inventory.
   */
  public Map<String, Map<String, String>> executeInventory() {
    logger.debug("executeInventory, query[" + getQuery() + "]");

    DatabaseResult result = executeQuery(DbQueryType.INVENTORY, getQuery());

    return result.getInventoryResult();
  }

  /**
   * Update query parameter values.
   */
  public void updateQueryParameterValues(Map<String, Object> row) {
    if (row != null) {
      this.queryParameterValues.clear();

      for (int x = 0; x < this.queryParameterColumns.size(); x++) {
        final String name = this.queryParameterColumns.get(x).toLowerCase(Locale.ENGLISH);

        if (row.containsKey(name)) {
          logger.info("Updating Query Parameter value: '" + name + "' = [" + row.get(name) + "]");
          this.queryParameterValues.add(x, row.get(name));
        } else {
          logger.error("Could not find query parameter [" + name + "] in result set");
        }
      }
    }
  }

  /**
   * This makes a pre-first-run query that is different that the normal query, and
   * stores the results for use later. The purpose of this is to allow for dynamic
   * seeding of parameterized queries.
   * <p>
   * The main use case is to jump to the end of a table and start tailing it. To
   * accomplish this, select the highest ID (hopefully you have an ID column) in
   * the Initial Query, then use that in your standard query as a parameter.
   * </p>
   * <p>
   * Initial Query: SELECT max(id) as 'id' FROM MyTable;
   * </p>
   * <p>
   * Metric Query: SELECT id, thing, otherThing FROM MyTable WHERE id &gt; ?;
   * </p>
   * <p>
   * NOTE! It is important that the first query and all subsequent queries return
   * the column with the same name. In this case 'id'.
   * </p>
   *
   * @return True if successfully run, False otherwise
   */
  public boolean executeInitialQuery() {
    if (this.hasInitialQuery()) {
      logger.debug("Making initial query: [" + this.initialQuery + "]");

      DatabaseResult result = executeQuery(DbQueryType.RAW, getInitialQuery());

      Map<String, Object> firstRow = result.getRawResult().get(0);

      if (firstRow != null) {
        updateQueryParameterValues(firstRow);
        this.runInitialQuery = false;
        return true;
      }

      this.runInitialQuery = false; // Even if we failed, don't keep running this
    }

    return false;
  }

  /**
   * Prepare the given prepared SQL statement.
   */
  public void prepareStatement(PreparedStatement statement) throws Exception {
    try {
      for (int x = 0; x < this.queryParameterValues.size(); x++) {
        statement.setObject(x + 1, this.queryParameterValues.get(x));
      }
    } catch (SQLException e) {
      logger.error("Unable to prepare statement", e);
      throw e;
    } catch (NullPointerException e) {
      logger.error("Null statement passed, unable to process");
      throw e;
    }
  }

  /**
   * Generate a hash of the entire row in an attempt to deduplicate data.
   *
   * @param resultSet         Data to read from, should point at the requested row
   *                          to hash
   * @param resultSetMetaData Needed to know how wide the row is, data about the
   *                          columns
   * @return String containing hash calculated for the row
   */
  public String calculateHash(ResultSet resultSet, ResultSetMetaData resultSetMetaData) {
    String rowHash = null;

    // Grab all the bytes from the row, checksum it, and return if we had a
    // duplicate
    try {
      if ((resultSet != null) && (resultSetMetaData != null)) {
        final ByteBuffer rawRow = ByteBuffer.allocate(rowBufferSize);
        for (int c = 1; c < resultSetMetaData.getColumnCount(); c++) {
          byte[] coldata = resultSet.getBytes(c);

          if (coldata != null) {
            rawRow.put(coldata);
          }
        }

        rowHash = DigestUtils.md5Hex(rawRow.array());
      }
    } catch (SQLException | BufferOverflowException | NullPointerException e) {
      logger.error("Unable to check row for duplicate", e);
      return null;
    }

    return rowHash;
  }

  private DatabaseResult executeQuery(DbQueryType queryType, String query) {
    DatabaseResult result = new DatabaseResult();
    this.updateDefaultMetrics(); // Update the set of Default Metrics returned

    Connection con = null;
    PreparedStatement statement = null;
    ResultSet rs = null;
    int rowsParsed = 0;
    int rowDuplicates = 0;
    int rowsTotal = 0;
    String rowHash = null;

    try {
      parser.beforeQuery(); // Make sure the parser is ready

      con = getConnection();
      statement = con.prepareStatement(
          query,
          ResultSet.TYPE_SCROLL_INSENSITIVE,
          ResultSet.CONCUR_READ_ONLY
      );

      if (this.queryParameterValues.size() > 0) {
        prepareStatement(statement); // Insert any data that we have for the query
      }

      if (parser.isStateful()) {
        // Allow the Parser to modify the query if it is keeping state
        parser.prepareStatement(statement);
      }

      logger.debug("Executing statement [ " + statement.toString() + " ]");
      rs = statement.executeQuery();

      final ResultSetMetaData rsmd = rs.getMetaData();

      this.setTableName(rsmd.getTableName(1)); // Capture the TableName
      defaultMetrics.add(new AttributeMetric("tableName", this.getTableName()));

      processRows: while (rs.next()) {
        try {
          if (this.deduplicate) {
            rowHash = calculateHash(rs, rsmd);

            if ((rowHash != null) && uniqueHistoryList.containsKey(rowHash)) {
              int times = uniqueHistoryList.get(rowHash);
              times += 1;
              uniqueHistoryList.put(rowHash, times);
              logger.debug(
                  "Found duplicate row with hash: '"
                  + rowHash
                  + "' again, total count: '"
                  + times
                  + "'"
              );
              rowDuplicates += 1;
              continue; // Skip the row, it is a duplicate
            }

          }

          switch (queryType) {
            case METRIC:
              List<Metric> rowList = parser.parseMetricRow(this.getMetricType(), rs, rsmd);

              if (rowList != null && !rowList.isEmpty()) {
                rowList.addAll(this.getDefaultMetrics()); // Include our defaults
                result.addMetricResult(rowList);
                rowsParsed += 1;
              }
              break;
            case INVENTORY:
              if (result.addInventoryResult(
                  getInventoryPath(),
                  parser.parseInventoryRow(rs, rsmd)
              )) {
                rowsParsed += 1;
              }
              break;
            case RAW:
              if (result.addRawResult(parser.parseRawRow(rs, rsmd))) {
                rowsParsed += 1;
              }
              break;
            default:
              logger.error("Undefined queryType");
              break processRows;
          }

          if (this.deduplicate && rowHash != null) {
            // If we parsed the row, track the hash for deduplication
            uniqueHistoryList.put(rowHash, 1);
          }
        } catch (SQLException e) {
          logger.error("Failed to parse row, skipping", e);
        } finally {
          rowsTotal += 1;
        }
      }

      // Reparse the last row to update any offsets we're keeping
      if (this.queryParameterValues.size() > 0) {
        if (rs.last()) {
          Map<String, Object> lastRow = parser.parseRawRow(rs, rsmd);

          if (lastRow != null) {
            updateQueryParameterValues(lastRow);
          }
        }
      }
      parser.afterQuery(); // Perform any cleanup needed by the parser
    } catch (SQLException e) {
      logger.error("SQL Error Query: [" + query + "]", e);
      addSqlExceptionToResult(e, result);
    } catch (Exception e) {
      logger.error("Unknown Exception caught", e);
    } finally {
      try {
        if (statement != null) {
          statement.close();
        }

        if (rs != null) {
          rs.close();
        }

        if (con != null) {
          con.close();
        }
      } catch (Exception e) {
        logger.error("Error Closing Connections" + e);
      }
      rs = null;
      statement = null;
      con = null;
    }

    logger.info(
        "Successfully parsed "
        + rowsParsed
        + "/"
        + rowsTotal
        + " rows ("
        + rowDuplicates
        + " duplicates)"
    );

    return result;
  }

  private void addSqlExceptionToResult(SQLException e, DatabaseResult result) {
    List<Metric> metrics = new ArrayList<>();
    metrics.addAll(this.getDefaultMetrics());
    metrics.add(new AttributeMetric("errorCode", e.getErrorCode()));
    metrics.add(new AttributeMetric("errorMessage", e.getMessage()));
    result.addMetricResult(metrics);
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * Get the database name.
   */
  public String getDatabase() {
    if (database == null) {
      return defaultDb;
    }
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  /**
   * Get the table name.
   */
  public String getTableName() {
    if (this.tableName != null && !this.tableName.isEmpty()) {
      return this.tableName;
    } else {
      return DatabaseCommand.defaultTable;
    }
  }

  /**
   * Set thte table name.
   */
  public void setTableName(String tableName) {
    if (tableName == null || tableName.equals("")) {
      this.tableName = DatabaseCommand.defaultTable;
    } else {
      this.tableName = tableName;
    }
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getInitialQuery() {
    return initialQuery;
  }

  /**
   * Set the initial query.
   */
  public void setInitialQuery(String query) {
    if (query != null && !query.isEmpty()) {
      this.runInitialQuery = true;
      this.initialQuery = query;
    }
  }

  public boolean hasInitialQuery() {
    return this.runInitialQuery;
  }

  /**
   * Get the parser name.
   */
  public String getParserName() {
    if (this.parser != null) {
      return parser.getParserName();
    } else {
      return "Undefined";
    }
  }

  /**
   * Set the parser name.
   */
  public void setParser(String name) {
    if (name == null || name.equalsIgnoreCase(defaultParser)) {
      this.parser = new GenericParser();
    } else {
      logger.debug("Using parser: '" + name + "'");

      try {
        Class parserClass = Class.forName(name);
        this.parser = (DatabaseParser) parserClass.newInstance();
      } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
        // Failed for some reason, log and fallback to the generic parser
        logger.error("Unable to load Class '" + name + "' with exception: ", e);
        this.parser = new GenericParser();
      }

      if (this.parserOptions != null) {
        this.parser.setOptions(this.parserOptions);
      }
    }
  }

  public void setParserOptions(JsonObject options) {
    this.parserOptions = options;
  }

  public String getParserOptions() {
    return this.parserOptions.toString();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public boolean isSslConnection() {
    return sslConnection;
  }

  public void setSslConnection(boolean sslConnection) {
    this.sslConnection = sslConnection;
  }

  public boolean isSslEncrypt() {
    return sslEncrypt;
  }

  public void setSslEncrypt(boolean sslEncrypt) {
    this.sslEncrypt = sslEncrypt;
  }

  public boolean isSslTrustServerCert() {
    return sslTrustServerCert;
  }

  public void setSslTrustServerCert(boolean sslTrustServerCert) {
    this.sslTrustServerCert = sslTrustServerCert;
  }

  public String getSslHostnameInCert() {
    return sslHostnameInCert;
  }

  public void setSslHostnameInCert(String sslHostnameInCert) {
    this.sslHostnameInCert = sslHostnameInCert;
  }

  public String getSslTrustStoreLocation() {
    return sslTrustStoreLocation;
  }

  public void setSslTrustStoreLocation(String sslTrustStoreLocation) {
    this.sslTrustStoreLocation = sslTrustStoreLocation;
  }

  public String getSslTrustStorePassword() {
    return sslTrustStorePassword;
  }

  public void setSslTrustStorePassword(String sslTrustStorePassword) {
    this.sslTrustStorePassword = sslTrustStorePassword;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  /**
   * Get the prefix.
   */
  public String getPrefix() {
    if (prefix == null) {
      return "_";
    }
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getEventText() {
    return eventText;
  }

  public void setEventText(String eventText) {
    this.eventText = eventText;
  }

  public String getMetricType() {
    return metricType;
  }

  public void setMetricType(String metricType) {
    this.metricType = metricType;
  }

  public List<Metric> getDefaultMetrics() {
    return this.defaultMetrics;
  }

  public void setDeduplicate(boolean deduplicate) {
    this.deduplicate = deduplicate;
  }

  public boolean getDeduplicate() {
    return deduplicate;
  }

  /**
   * Set the row buffer size.
   */
  public void setRowBufferSize(int size) {
    if (size > 0) {
      this.rowBufferSize = size;
    }
  }

  public int getRowBufferSize() {
    return rowBufferSize;
  }

  /**
   * Set the unique history size.
   */
  public void setUniqueHistorySize(int uniqueHistorySize) {
    if (uniqueHistorySize > 0) {
      this.uniqueHistorySize = uniqueHistorySize;
    }
  }

  public int getUniqueHistorySize() {
    return uniqueHistorySize;
  }

  /**
   * Update the default metrics.
   */
  public void updateDefaultMetrics() {
    defaultMetrics.clear();

    defaultMetrics.add(new AttributeMetric("event_type", this.getEventType()));
    defaultMetrics.add(new AttributeMetric("database", this.getDatabase()));
    defaultMetrics.add(new AttributeMetric("queryName", this.getName()));
    defaultMetrics.add(new AttributeMetric("query", this.getQuery()));
    defaultMetrics.add(new AttributeMetric("databaseHost", this.getHostname()));
  }

  /**
   * Get the inventory path.
   */
  public String getInventoryPath() {
    String path = this.getPrefix() + "/" + this.getDatabase() + "/" + this.getTableName();

    return path.toLowerCase(Locale.ENGLISH);
  }

  /**
   * Parses the 'queryOptions' config file item and sets variables as needed.
   * <p>
   * current format:
   * <code>
   * "queryOptions": { "initialQuery": "SELECT max(id) as 'ID', timestamp FROM
   * MyTable", "queryParameterColumns": [ "ID", "timestamp" ] }
   * </code>
   * </p>
   *
   * @param options JSON Object describing all the options.
   */
  public void setQueryOptions(JsonObject options) {
    try {
      StringBuilder queryColumnNames = new StringBuilder("");

      if (options.get("initialQuery") != null) {
        this.setInitialQuery(options.get("initialQuery").getAsString());
      }

      if (options.get("queryParameterColumns") != null) {
        this.queryParameterColumns.clear();

        JsonArray columns = options.getAsJsonArray("queryParameterColumns");

        for (int x = 0; x < columns.size(); x++) {
          String name = columns.get(x).getAsString();
          this.queryParameterColumns.add(x, name);
          queryColumnNames.append(name).append(" ");
        }
      }

      logger.info(
          "Options: initialQuery = ["
          + this.getInitialQuery()
          + "], queryParameterColumns = ["
          + queryColumnNames.toString().trim()
          + "]"
      );
    } catch (NullPointerException e) {
      logger.error("Required parserOptions parameter missing");
      throw e; // Still want to throw this, however now we'll know where to look to fix it
    }
  }
}
