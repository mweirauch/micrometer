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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.cache.Cache;
import javax.management.*;
import java.util.List;

/**
 * See https://github.com/jsr107/demo/blob/master/src/test/java/javax/cache/core/StatisticsExample.java
 *
 * @author Jon Schneider
 */
public class JCacheMetrics implements MeterBinder {
    /**
     * Defining cache statistics parameters as constants.
     */
    private enum CacheStatistics {
        CacheHits, CacheHitPercentage,
        CacheMisses, CacheMissPercentage,
        CacheGets, CachePuts, CacheRemovals, CacheEvictions,
        AverageGetTime, AveragePutTime, AverageRemoveTime;

        public long get(ObjectName objectName) {
            try {
                List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
                System.out.println("There are " + mBeanServers.size() + " MBean servers");
                for (MBeanServer mBeanServer : mBeanServers) {
                    try {
                        Object attribute = mBeanServer.getAttribute(objectName, this.toString());
                        return (Long) attribute;
                    } catch (AttributeNotFoundException | InstanceNotFoundException ex) {
                        // did not find MBean, try the next server
                    }
                }
            } catch (MBeanException | ReflectionException ex) {
                throw new IllegalStateException(ex);
            }

            // didn't find the MBean in any servers
            return 0;
        }
    }

    private ObjectName objectName;
    private final String name;
    private final Iterable<Tag> tags;

    public JCacheMetrics(Cache<?, ?> cache, String name, Iterable<Tag> tags) {
        try {
            String cacheManagerUri = cache.getCacheManager().getURI().toString()
                .replace(':', '.'); // ehcache's uri is prefixed with 'urn:'

            this.objectName = new ObjectName("javax.cache:type=CacheStatistics"
                + ",CacheManager=" + cacheManagerUri
                + ",Cache=" + cache.getName());
        } catch (MalformedObjectNameException ignored) {
            throw new IllegalStateException("Cache name '" + cache.getName() + "' results in an invalid JMX name");
        }
        this.name = name;
        this.tags = Tags.concat(tags, "name", cache.getName());
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(registry.createId(name + ".requests", Tags.concat(tags, "result", "hit"),
            "The number of times cache lookup methods have returned a cached value"),
            objectName, CacheStatistics.CacheHits::get);

        registry.gauge(registry.createId(name + ".requests", Tags.concat(tags, "result", "miss"),
            "The number of times cache lookup methods have not returned a value"),
            objectName, CacheStatistics.CacheMisses::get);

        registry.gauge(registry.createId(name + ".puts", tags, "Cache removals"),
            objectName, CacheStatistics.CachePuts::get);

        registry.gauge(registry.createId(name + ".removals", tags, "Cache removals"),
            objectName, CacheStatistics.CacheRemovals::get);

        registry.gauge(registry.createId(name + ".evictions", tags, "Cache evictions"),
            objectName, CacheStatistics.CacheEvictions::get);
    }
}
