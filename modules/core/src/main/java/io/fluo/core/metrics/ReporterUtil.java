/*
 * Copyright 2014 Fluo authors (see AUTHORS)
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

package io.fluo.core.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import com.codahale.metrics.MetricRegistry;
import io.fluo.api.config.FluoConfiguration;
import io.fluo.core.impl.Environment;
import io.fluo.core.impl.FluoConfigurationImpl;
import io.fluo.core.metrics.ReporterStarter.Params;
import org.apache.commons.configuration.Configuration;
import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramResetOnSnapshotReservoir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReporterUtil {

  private static final Logger log = LoggerFactory.getLogger(ReporterUtil.class);

  public static AutoCloseable setupReporters(final Environment env) {
    return setupReporters(env, FluoConfiguration.FLUO_PREFIX);
  }

  public static AutoCloseable setupReporters(final Environment env, final String domain) {
    ServiceLoader<ReporterStarter> serviceLoader = ServiceLoader.load(ReporterStarter.class);

    final List<AutoCloseable> allReporters = new ArrayList<>();

    for (ReporterStarter rs : serviceLoader) {
      List<AutoCloseable> reporters = rs.start(new Params() {

        @Override
        public Configuration getConfiguration() {
          return env.getConfiguration();
        }

        @Override
        public MetricRegistry getMetricRegistry() {
          return env.getSharedResources().getMetricRegistry();
        }

        @Override
        public String getDomain() {
          return domain;
        }
      });


      allReporters.addAll(reporters);
    }

    final String hdrSnapshotClass = HdrHistogramResetOnSnapshotReservoir.class.getName();
    String clazz =
        env.getConfiguration().getString(FluoConfigurationImpl.METRICS_RESERVOIR_PROP,
            hdrSnapshotClass);
    if ((allReporters.size() > 1) && (clazz.equals(hdrSnapshotClass))) {
      throw new IllegalStateException("Multiple metrics reporters cannot be configured when using "
          + hdrSnapshotClass + " as corrupt metrics can be reported");
    }

    log.info("Started {} metrics reporters", allReporters.size());

    return new AutoCloseable() {

      @Override
      public void close() {
        for (AutoCloseable closeable : allReporters) {
          try {
            closeable.close();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    };
  }
}
