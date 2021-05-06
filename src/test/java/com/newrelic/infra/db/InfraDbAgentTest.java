package com.newrelic.infra.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.newrelic.infra.db.command.DatabaseCommand;
import com.newrelic.infra.db.command.HsqlDbCommand;
import com.newrelic.infra.publish.api.metrics.Metric;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Test for InfraDbAgent.
 */
public class InfraDbAgentTest {

  private static String testDbUser = "SA";
  private static String testDbPass = "SA";
  private static InfraDbAgent agent = null;

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  /**
   * Setup method to load DB Class and init DB with Data.
   */
  @BeforeClass
  public static void createAgent() throws IOException, ClassNotFoundException, SQLException {
    agent = new InfraDbAgent(
        "Test",
        "locahost",
        1234,
        "SA",
        "SA",
        "src/test/resources/input.json",
        false,
        null,
        null
    );

    Class.forName("org.hsqldb.jdbc.JDBCDriver");
    initDatabase();
  }

  /**
   * Database Initialization Code.
   */
  private static void initDatabase() throws SQLException {
    try (
        Connection connection = getConnection();
        Statement statement = connection.createStatement();
    ) {
      statement.execute("CREATE TABLE EMPLOYEE (id INT NOT NULL, name VARCHAR(50) NOT NULL,"
          + "EMAIL VARCHAR(50) NOT NULL, PRIMARY KEY (id))");
      connection.commit();
      statement.executeUpdate("INSERT INTO EMPLOYEE VALUES (1001,'Joe Doe', 'joe@doe.com')");
      statement.executeUpdate("INSERT INTO EMPLOYEE VALUES (1002,'Jane Doe', 'jane@doe.com')");
      statement.executeUpdate("INSERT INTO EMPLOYEE VALUES (1003,'Peter Doe', 'peter@doe.com')");
      connection.commit();

      // Table that has duplicates
      statement.execute(
          "CREATE TABLE LOG (ts TIMESTAMP NOT NULL, num INT NOT NULL, data VARCHAR(50) NOT NULL,"
          + "hostname VARCHAR(50) NOT NULL)");
      connection.commit();
      statement.executeUpdate(
          "INSERT INTO LOG VALUES (TIMESTAMP '2018-02-20 10:00:00.000000', 1, 'test', 'localhost');"
      );
      statement.executeUpdate(
          "INSERT INTO LOG VALUES (TIMESTAMP '2018-02-20 10:00:00.000000', 1, 'test', 'localhost');"
      );
      statement.executeUpdate(
          "INSERT INTO LOG VALUES (TIMESTAMP '2018-02-20 10:00:00.000000', 1,"
          + "'test two', 'localhost');"
      );
      connection.commit();
    }
  }

  private static Connection getConnection() throws SQLException {
    return DriverManager.getConnection("jdbc:hsqldb:mem:EMPLOYEE", testDbUser, testDbPass);
  }

  /**
   * Testing the input.json load/commands.
   */
  @Test
  public void testCommands() throws IOException {

    assertNotNull(agent);
    List<DatabaseCommand> commandList = agent.getCommands("src/test/resources/input.json");
    assertNotNull(commandList);

    assertTrue(commandList.size() > 0);

    DatabaseCommand command = commandList.get(0);
    assertNotNull(command);

    assertTrue(command instanceof HsqlDbCommand);
    assertEquals(command.getQuery(), "SELECT * FROM EMPLOYEE");
    assertEquals(command.getName(), "Metric Test");
  }

  /**
   * Testing the Inventory Command.
   */
  @Test
  public void testInventory() throws IOException {
    assertNotNull(agent);
    List<DatabaseCommand> commandList = agent.getCommands("src/test/resources/input.json");
    assertNotNull(commandList);

    DatabaseCommand command = commandList.get(1);

    Map<String, Map<String, String>> inventory = command.executeInventory();

    assertNotNull(inventory);
    assertTrue(inventory.size() > 0);
  }

  /**
   * Testing the Metric Command.
   */
  @Test
  public void testMetrics() throws IOException, Exception {
    assertNotNull(agent);
    List<DatabaseCommand> commandList = agent.getCommands("src/test/resources/input.json");
    assertNotNull(commandList);

    DatabaseCommand command = commandList.get(0);

    List<List<Metric>> metrics = command.executeMetric();
    assertNotNull(metrics);

    assertTrue(metrics.size() > 0);
  }

  /**
   * Testing the Metric Command.
   */
  @Test
  public void testDeduplicate() throws IOException, Exception {
    assertNotNull(agent);
    List<DatabaseCommand> commandList
        = agent.getCommands("src/test/resources/input_duplicate.json");
    assertNotNull(commandList);

    DatabaseCommand command = commandList.get(0);

    List<List<Metric>> metrics = command.executeMetric();
    assertNotNull(metrics);

    assertTrue(metrics.size() == 2); // Three rows, two are unique
  }

  /**
   * Testing the input.json load/commands.
   */
  @Test
  public void testInputJsonWrongDb() throws IOException {

    assertNotNull(agent);
    exception.expect(NullPointerException.class);
    List<DatabaseCommand> commandList = agent.getCommands("src/test/resources/input_wrongdb.json");
    assertNotNull(commandList);

  }

  @Test
  public void testInputJsonWrongType() throws IOException {

    assertNotNull(agent);
    List<DatabaseCommand> commandList
        = agent.getCommands("src/test/resources/input_wrongtype.json");
    assertNotNull(commandList);

    // Should return 0
    assertTrue(commandList.size() == 0);

  }

  @Test
  public void testInputJsonMissProperty() throws IOException {

    assertNotNull(agent);
    // exception.expect(NullPointerException.class);
    List<DatabaseCommand> commandList
        = agent.getCommands("src/test/resources/input_missproperty.json");
    assertNotNull(commandList);

    DatabaseCommand command = commandList.get(0);

    List<List<Metric>> metrics = command.executeMetric();
    assertNotNull(metrics);

    assertTrue(metrics.size() > 0);
  }

  @Test
  public void testInputJsonMissing() throws IOException {

    assertNotNull(agent);
    exception.expect(FileNotFoundException.class);
    List<DatabaseCommand> commandList = agent.getCommands("src/test/resources/nofilethere.json");
    assertNotNull(commandList);
  }

}
