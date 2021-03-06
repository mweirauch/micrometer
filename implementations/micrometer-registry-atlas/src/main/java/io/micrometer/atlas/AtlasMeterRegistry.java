/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.atlas;

import com.netflix.spectator.api.histogram.PercentileDistributionSummary;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.spectator.SpectatorDistributionSummary;
import io.micrometer.core.instrument.spectator.SpectatorTimer;
import io.micrometer.core.instrument.spectator.step.StepSpectatorMeterRegistry;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.hist.PercentileHistogram;
import io.micrometer.core.instrument.stats.hist.PercentileTimeHistogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.function.UnaryOperator;

/**
 * @author Jon Schneider
 */
public class AtlasMeterRegistry extends StepSpectatorMeterRegistry {
    public AtlasMeterRegistry(AtlasConfig config, Clock clock) {
        // The Spectator Atlas registry will do tag formatting for us, so we'll just pass through
        // tag keys and values with the identity formatter.
        super(null, new AtlasRegistry(new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return clock.wallTime();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        }, config), clock, config.step().toMillis());

        // invalid character replacement happens in the spectator-reg-atlas module, so doesn't need
        // to be duplicated here.
        this.config().namingConvention(NamingConvention.camelCase);

        start();
    }

    public AtlasMeterRegistry(AtlasConfig config) {
        this(config, Clock.SYSTEM);
    }

    public void start() {
        getAtlasRegistry().start();
    }

    public void stop() {
        getAtlasRegistry().stop();
    }

    private AtlasRegistry getAtlasRegistry() {
        return (AtlasRegistry) this.getSpectatorRegistry();
    }

    @Override
    protected Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        if (histogram != null && histogram.create(Histogram.Summation.Normal) instanceof PercentileTimeHistogram) {
            // scale nanosecond precise quantile values to seconds
            registerQuantilesGaugeIfNecessary(id, quantiles, t -> t / 1.0e6);
            com.netflix.spectator.api.Timer timer = PercentileTimer.get(getSpectatorRegistry(), getSpectatorRegistry().createId(getConventionName(id), toSpectatorTags(getConventionTags(id))));
            return new SpectatorTimer(id, timer, clock, quantiles, null);
        }

        return super.newTimer(id, histogram, quantiles);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        if(histogram != null && histogram.create(Histogram.Summation.Normal) instanceof PercentileHistogram) {
            registerQuantilesGaugeIfNecessary(id, quantiles, UnaryOperator.identity());
            com.netflix.spectator.api.DistributionSummary ds = PercentileDistributionSummary.get(getSpectatorRegistry(), getSpectatorRegistry().createId(getConventionName(id),
                toSpectatorTags(getConventionTags(id))));
            return new SpectatorDistributionSummary(id, ds, quantiles, null);
        }

        return super.newDistributionSummary(id, histogram, quantiles);
    }
}
