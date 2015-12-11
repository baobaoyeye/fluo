/*
 * Copyright 2015 Fluo authors (see AUTHORS)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.fluo.core.metrics.starters;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Slf4jReporter;
import io.fluo.api.config.FluoConfiguration;
import io.fluo.core.metrics.ReporterStarter;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jReporterStarter implements ReporterStarter {

  private static final Logger log = LoggerFactory.getLogger(Slf4jReporterStarter.class);

  @Override
  public List<AutoCloseable> start(Params params) {
    Configuration config =
        new FluoConfiguration(params.getConfiguration()).getReporterConfiguration("slf4j");

    if (!config.getBoolean("enable", false)) {
      return Collections.emptyList();
    }

    TimeUnit rateUnit = TimeUnit.valueOf(config.getString("rateUnit", "seconds").toUpperCase());
    TimeUnit durationUnit =
        TimeUnit.valueOf(config.getString("durationUnit", "milliseconds").toUpperCase());
    Logger logger = LoggerFactory.getLogger(config.getString("logger", "metrics"));

    Slf4jReporter reporter =
        Slf4jReporter.forRegistry(params.getMetricRegistry()).convertDurationsTo(durationUnit)
            .convertRatesTo(rateUnit).outputTo(logger).build();
    reporter.start(config.getInt("frequency", 60), TimeUnit.SECONDS);

    log.info("Reporting metrics using slf4j");

    return Collections.singletonList((AutoCloseable) reporter);
  }

}
