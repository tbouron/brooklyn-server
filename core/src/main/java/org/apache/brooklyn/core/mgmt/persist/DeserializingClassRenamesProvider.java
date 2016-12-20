/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.mgmt.persist;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.osgi.Compat;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.brooklyn.util.stream.Streams;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

@Beta
public class DeserializingClassRenamesProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DeserializingClassRenamesProvider.class);

    public static final String DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH = "classpath://org/apache/brooklyn/core/mgmt/persist/deserializingClassRenames.properties";
    public static final String KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES = "org.apache.brooklyn.class-rename";

    private static volatile Map<String, String> cache;

    public static DeserializingClassRenamesProvider INSTANCE;

    private ConfigurationAdmin configAdmin;

    @Beta
    public static Map<String, String> loadDeserializingClassRenames() {
        if (DeserializingClassRenamesProvider.INSTANCE == null) {
            throw new IllegalStateException("Cannot load class rename properties because brooklyn hasn't started yet");
        }
        return DeserializingClassRenamesProvider.INSTANCE.loadDeserializingClassRenamesMap();
    }

    public static void initInstance() {
        final DeserializingClassRenamesProvider deserializingClassRenamesProvider = new DeserializingClassRenamesProvider();
        deserializingClassRenamesProvider.init();
    }

    public static void reset() {
        synchronized (DeserializingClassRenamesProvider.class) {
            cache = null;
        }
    }

    public DeserializingClassRenamesProvider() {
        LOG.debug("DeserializingClassRenamesProvider instance created", new Exception("for stack trace"));
    }

    public void init() {
        LOG.debug("DeserializingClassRenamesProvider init called");
        this.loadDeserializingClassRenamesMap();
        INSTANCE = this;
        LOG.debug("DeserializingClassRenamesProvider init finished");
    }

    public void updateProperties(Map properties) {
        synchronized (DeserializingClassRenamesProvider.class) {
            cache = this.loadDeserializingClassRenamesCache();
        }
    }

    public void setConfigAdmin(ConfigurationAdmin configAdmin) {
        this.configAdmin = configAdmin;
    }

    public Map<String, String> loadDeserializingClassRenamesMap() {
        // Double-checked locking - got to use volatile or some such!
        if (cache == null) {
            synchronized (DeserializingClassRenamesProvider.class) {
                if (cache == null) {
                    cache = this.loadDeserializingClassRenamesCache();
                }
            }
        }
        return cache;
    }

    private Map<String, String> loadDeserializingClassRenamesCache() {
        final ImmutableMap<String, String> mapping = ImmutableMap.<String, String>builder()
                .putAll(this.loadDeserializingClassRenamesFromBrooklynDir())
                .putAll(this.loadDeserializingClassRenameFromOSGi())
                .build();
        LOG.debug("Class rename cache size: ", mapping.size());
        return mapping;
    }

    private Map<String, String> loadDeserializingClassRenamesFromBrooklynDir() {
        try {
            InputStream resource = new ResourceUtils(DeserializingClassRenamesProvider.class).getResourceFromUrl(DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH);

            try {
                Properties props = new Properties();
                props.load(resource);

                Map<String, String> result = Maps.newLinkedHashMap();
                for (Enumeration<?> iter = props.propertyNames(); iter.hasMoreElements(); ) {
                    String key = (String) iter.nextElement();
                    String value = props.getProperty(key);
                    result.put(key, value);
                }
                return result;
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            } finally {
                Streams.closeQuietly(resource);
            }
        } catch (Exception e) {
            return ImmutableMap.<String, String>of();
        }
    }

    private Map<String, String> loadDeserializingClassRenameFromOSGi() {
        if (configAdmin == null) {
            LOG.debug("No OSGi configuration, you probably run in classic mode");
            return ImmutableMap.of();
        }

        String filter = '(' + Constants.SERVICE_PID + '=' + KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES + ')';
        Configuration[] configs;

        try {
            LOG.info("Listing in DeserializingClassRenamesProvider of configAdmin.listConfigurations(null):", configAdmin.listConfigurations(null));
            configs = configAdmin.listConfigurations(null);
        } catch (InvalidSyntaxException | IOException e) {
            LOG.info("Cannot list OSGi configurations");
            throw Exceptions.propagate(e);
        }

        final MutableMap<String, String> map = MutableMap.of();
        if (configs != null) {
            for (Configuration config : configs) {
                LOG.info("Reading OSGi configuration from " + config.getPid());
                if (KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES.equals(config.getPid())) {
                    map.putAll(dictToMap(config.getProperties()));
                }
            }
        } else {
            LOG.info("tbouron:::: No OSGi configuration found for " + KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES + ".cfg");
        }

        return map;
    }

    private static Map<String, String> dictToMap(Dictionary<String, Object> props) {
        Map<String, String> mapProps = MutableMap.of();
        Enumeration<String> keyEnum = props.keys();
        while (keyEnum.hasMoreElements()) {
            String key = keyEnum.nextElement();
            mapProps.put(key, (String) props.get(key));
        }
        return mapProps;
    }

//    public void contextInitialized() {
//
//        final BundleContext bundleContext = FrameworkUtil.getBundle(DeserializingClassRenamesProvider.class).getBundleContext();
//        bundleContext.addBundleListener(new BundleListener() {
//            @Override
//            public void bundleChanged(BundleEvent bundleEvent) {
//                final URL resource = bundleEvent.getBundle().getResource("org.apache.brooklyn.persistence.class.rename.properties");
//                // Read properties file
//                switch (bundleEvent.getType()) {
//                    case BundleEvent.STARTED:
//                        // Add properties to the cache
//                        break;
//                    case BundleEvent.STOPPED:
//                        // Remove properties from the cache
//                        break;
//                }
//            }
//        });
//    }

    @Beta
    public static String findMappedName(String name) {
        return Reflections.findMappedNameAndLog(DeserializingClassRenamesProvider.loadDeserializingClassRenames(), name);
    }
}
