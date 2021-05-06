package com.newrelic.infra.db.command;

import com.newrelic.infra.publish.api.metrics.Metric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The database result class.
 */
public class DatabaseResult {
  private List<Map<String, Object>> rawResult;
  private List<List<Metric>> metricResult;
  private Map<String, Map<String, String>> inventoryResult;

  /**
   * Create a new database result.
   */
  public DatabaseResult() {
    this.rawResult = new ArrayList<>();
    this.metricResult = new ArrayList<List<Metric>>();
    this.inventoryResult = new HashMap<String, Map<String, String>>();
  }

  /**
   * Add a native Java result to the DatabaseResult.
   *
   * @param result Map to add.
   * @return True on success, False of failure
   */
  public boolean addRawResult(Map<String, Object> result) {
    if ((result != null) && !result.isEmpty()) {
      this.rawResult.add(result);
      return true;
    }
    return false;
  }

  /**
   * Gather the list of raw results.
   *
   * @return List of the HashMap of Objects.
   */
  public List<Map<String, Object>> getRawResult() {
    return this.rawResult;
  }

  /**
   * Add to the Metric set.
   *
   * @param metric Metric list to add.
   * @return True on success, False on failure
   */
  public boolean addMetricResult(List<Metric> metric) {
    if ((metric != null) && !metric.isEmpty()) {
      this.metricResult.add(metric);
      return true;
    }

    return false;
  }

  /**
   * Return the entire set of Metrics.
   *
   * @return Metric set.
   */
  public List<List<Metric>> getMetricResult() {
    return this.metricResult;
  }

  /**
   * Add the inventory data to the HashMap.
   *
   * @param path            Key for inserting the HashMap.
   * @param inventoryResult Inventory HashMap to add
   * @return True on success, False on failure
   */
  public boolean addInventoryResult(String path, Map<String, String> inventoryResult) {
    if (
        (inventoryResult != null)
        && !inventoryResult.isEmpty() && (path != null) && !path.isEmpty()
    ) {

      this.inventoryResult.put(path, inventoryResult);
      return true;
    }

    return false;
  }

  /**
   * Return the entire Inventory.
   *
   * @return HashMap (of HashMaps) that contains the inventory data.
   */
  public Map<String, Map<String, String>> getInventoryResult() {
    return this.inventoryResult;
  }
}
