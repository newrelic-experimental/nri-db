package com.newrelic.infra.db;

import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.newrelic.infra.db.command.As400Command;
import com.newrelic.infra.db.command.DatabaseCommand;
import com.newrelic.infra.db.command.Db2Command;
import com.newrelic.infra.db.command.HsqlDbCommand;
import com.newrelic.infra.db.command.MsSqlCommand;
import com.newrelic.infra.db.command.MySqlCommand;
import com.newrelic.infra.db.command.OracleCommand;
import com.newrelic.infra.db.command.PostgresCommand;
import com.newrelic.infra.publish.api.Agent;
import com.newrelic.infra.publish.api.InventoryReporter;
import com.newrelic.infra.publish.api.MetricReporter;
import com.newrelic.infra.publish.api.metrics.Metric;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The NRI DB agent.
 */
public class InfraDbAgent extends Agent {

  private static final Logger logger = LoggerFactory.getLogger(InfraDbAgent.class);
  private static final String METRIC = "metric";
  private static final String INVENTORY = "inventory";

  private List<DatabaseCommand> commands = null;
  private String name = null;
  private String hostname = null;
  private Integer port = null;
  private String username = null;
  private String password = null;
  private boolean sslConnection = false;
  private boolean sslEncrypt = false;
  private boolean sslTrustServerCert = false;
  private String sslHostnameInCert = null;
  private String sslTrustStoreLocation = null;
  private String sslTrustStorePassword = null;

  /**
   * Create the agent.
   */
  public InfraDbAgent(
      String name,
      String hostname,
      Integer port,
      String username,
      String password,
      String inputfile,
      boolean sslConnection,
      boolean sslEncrypt,
      boolean sslTrustServerCert,
      String sslHostnameInCert,
      String sslTrustStoreLocation,
      String sslTrustStorePassword
  ) throws IOException {

    this.name = name;
    this.hostname = hostname;
    this.port = port;
    this.username = username;
    this.password = password;
    this.sslConnection = sslConnection;
    this.sslEncrypt = sslEncrypt;
    this.sslTrustServerCert = sslTrustServerCert;
    this.sslHostnameInCert = sslHostnameInCert;
    this.sslTrustStoreLocation = sslTrustStoreLocation;
    this.sslTrustStorePassword = sslTrustStorePassword;

    commands = getCommands(inputfile);

    /*
     * Loop through any Initial queries required by the above commands
     */
    for (final DatabaseCommand command : commands) {
      try {
        command.executeInitialQuery();
      } catch (Exception e) {
        logger.error("Initial query failed for [" + command.getName() + "]", e);
      }
    }
  }

  public void dispose() throws Exception {
    logger.info("dispose");
  }

  @Override
  public void populateInventory(InventoryReporter inventoryReporter) throws Exception {
    logger.debug("populateInventory");

    for (final DatabaseCommand command : commands) {
      try {
        if (command.getDataType().equalsIgnoreCase(INVENTORY)) {
          Map<String, Map<String, String>> inventory = command.executeInventory();
          for (Map.Entry<String, Map<String, String>> entry : inventory.entrySet()) {
            inventoryReporter.report(entry.getKey(), entry.getValue());
          }
        }
      } catch (Exception e) {
        logger.error("Error Running Commands", e);
      }
    }
  }

  @Override
  public void populateMetrics(MetricReporter metricReporter) throws Exception {
    logger.debug("populateMetrics");
    List<Metric> staticAttributes = getStaticAttributes();
    for (final DatabaseCommand command : commands) {
      try {
        if (command.getDataType().equalsIgnoreCase(METRIC)) {
          List<List<Metric>> ll = command.executeMetric();

          for (final List<Metric> list : ll) {
            if (staticAttributes != null) {
              list.addAll(staticAttributes);
            }
            // using instance identifier version to avoid collision
            metricReporter.report(
                command.getDbType(),
                list,
                this.name
                + "_"
                + command.getName()
                + "_"
                + command.getProvider()
                + "_"
                + command.getQuery()
            );
          }
        }
      } catch (Exception e) {
        logger.error("Error Running Commands", e);
      }
    }
  }

  /**
   * Return the correct DatabaseCommand Object based on the Provider Name.
   * <p>
   * ADD New providers here!
   * </p>
   *
   * @param provider Sting name of the provider requests
   * @return DatabaseCommand for correct Provider, or null
   */
  private DatabaseCommand createDatabaseByProvider(String provider) {
    DatabaseCommand command = null;

    if (provider.equalsIgnoreCase("AS400")) {
      command = new As400Command();
    } else if (provider.equalsIgnoreCase("DB2")) {
      command = new Db2Command();
    } else if (provider.equalsIgnoreCase("MSSQL")) {
      command = new MsSqlCommand();
    } else if (provider.equalsIgnoreCase("MySQL")) {
      command = new MySqlCommand();
    } else if (provider.equalsIgnoreCase("oracle")) {
      command = new OracleCommand();
    } else if (provider.equalsIgnoreCase("Postgres")) {
      command = new PostgresCommand();
    } else if (provider.equalsIgnoreCase("HSQLDB")) {
      command = new HsqlDbCommand();
    }
    return command;
  }

  /**
   * Load configuration from disk for all Database Commands specified for this
   * Agent.
   *
   * @param definitionFile JSON Configuration file to parse
   * @return List of Database Commands for this agent to run
   * @throws IOException If there is an issue with reading the file, and
   *                     IOException is thrown
   */
  protected List<DatabaseCommand> getCommands(final String definitionFile) throws IOException {
    logger.info("Reading JSON Input Config [" + definitionFile + "]");

    JsonParser parser = new JsonParser();
    JsonArray jarr = null;
    InputStream inputStream = null;
    Reader inputStreamReader = null;
    final List<DatabaseCommand> configs = new LinkedList<DatabaseCommand>();

    try {
      inputStream = new FileInputStream(definitionFile);
      inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

      jarr = (JsonArray) parser.parse(inputStreamReader);
    } catch (JsonIOException | JsonSyntaxException | FileNotFoundException e) {
      logger.error("Error reading command file: '" + definitionFile + "'", e);
      throw e;
    } finally {
      if (inputStreamReader != null) {
        inputStreamReader.close();
      }
      if (inputStream != null) {
        inputStream.close();
      }
    }

    for (Object o : jarr) {
      JsonObject jsonObject = (JsonObject) o;

      try {
        final String name = jsonObject.get("name").getAsString();
        final String provider = jsonObject.get("provider").getAsString();
        DatabaseCommand command = createDatabaseByProvider(provider);

        if (command == null) {
          logger.error("Unable to load '" + name + "', unknown provider: [" + provider + "]");
          continue;
        }

        command.setName(name);
        command.setUsername(this.username);
        command.setPassword(this.password);
        command.setHostname(this.hostname);
        command.setPort(this.port);
        command.setSslConnection(this.sslConnection);
        command.setSslEncrypt(this.sslEncrypt);
        command.setSslTrustServerCert(this.sslTrustServerCert);
        command.setSslHostnameInCert(this.sslHostnameInCert);
        command.setSslTrustStoreLocation(this.sslTrustStoreLocation);
        command.setSslTrustStorePassword(this.sslTrustStorePassword);

        /* ******************************************************************
         * Required Attributes
         * ******************************************************************/
        if (jsonObject.get("database") != null) {
          command.setDatabase(jsonObject.get("database").getAsString());
        } else {
          throw new IOException("[" + name + "] missing attribute 'database'");
        }
        if (jsonObject.get("query") != null) {
          command.setQuery(jsonObject.get("query").getAsString());
        } else {
          throw new IOException("[" + name + "] missing attribute 'query'");
        }
        if (jsonObject.get("type") != null) {
          command.setDataType(jsonObject.get("type").getAsString());
        } else {
          throw new IOException("[" + name + "] missing attribute 'type'");
        }

        /* ******************************************************************
         * Optional Attributes
         * ******************************************************************/

        if (jsonObject.get("prefix") != null) {
          command.setPrefix(jsonObject.get("prefix").getAsString());
        }

        if (jsonObject.get("metricType") != null) {
          command.setMetricType(jsonObject.get("metricType").getAsString());
        }

        // Get parserOptions before the parser is created
        if (jsonObject.get("parserOptions") != null) {
          command.setParserOptions(jsonObject.getAsJsonObject("parserOptions"));
        }

        // setParser creates the parser, doesn't just store the name
        if (jsonObject.get("parser") != null) {
          command.setParser(jsonObject.get("parser").getAsString());
        }

        if (jsonObject.get("queryOptions") != null) {
          command.setQueryOptions(jsonObject.getAsJsonObject("queryOptions"));
        }

        if (jsonObject.get("deduplicate") != null) {
          command.setDeduplicate(jsonObject.get("deduplicate").getAsBoolean());
        }

        if (jsonObject.get("uniqueHistorySize") != null) {
          command.setUniqueHistorySize(jsonObject.get("uniqueHistorySize").getAsInt());
        }

        if (jsonObject.get("rowBufferSize") != null) {
          command.setRowBufferSize(jsonObject.get("rowBufferSize").getAsInt());
        }

        // Per command override of eventType (default to provider)
        if (jsonObject.get("eventType") != null) {
          command.setEventType(jsonObject.get("eventType").getAsString());
        } else {
          command.setEventType(command.getProvider());
        }

        configs.add(command);
      } catch (NullPointerException e) {
        logger.error("Required attributes missing from file: '" + definitionFile + "'");
        throw e; // Still want to throw this, however now we'll know where to look to fix it
      }
    }
    return configs;
  }
}
