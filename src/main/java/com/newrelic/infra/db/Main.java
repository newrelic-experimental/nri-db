package com.newrelic.infra.db;

import com.newrelic.infra.publish.RunnerFactory;
import com.newrelic.infra.publish.api.Runner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The agent application entry point.
 */
public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  /**
   * Program entry.
   */
  public static void main(String[] args) {
    logger.info("Starting Agent ");
    try {
      Runner runner = RunnerFactory.getRunner();
      runner.add(new InfraDbAgentFactory());
      runner.setupAndRun(); // Never returns
    } catch (Throwable e) {
      logger.error("Error loading Agent ", e);
      System.exit(-1);
    }
  }
}