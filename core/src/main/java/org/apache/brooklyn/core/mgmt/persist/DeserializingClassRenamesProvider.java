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
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.brooklyn.util.stream.Streams;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Beta
public class DeserializingClassRenamesProvider {

    /*
     * This provider keeps a cache of the class-renames, which is lazily populated (see {@link #cache}. 
     * Calling {@link #reset()} will set this cache to null, causing it to be reloaded next time 
     * it is requested.
     * 
     * Loading the cache involves iterating over the {@link #loaders}, returning the union of 
     * the results from {@link Loader#load()}.
     * 
     * Initially, the only loader is the basic {@link ClasspathConfigLoader}.
     * 
     * However, when running in karaf the {@link OsgiConfigLoader} will be instantiated and added.
     * See karaf/init/src/main/resources/OSGI-INF/blueprint/blueprint.xml
     */
    
    private static final Logger LOG = LoggerFactory.getLogger(DeserializingClassRenamesProvider.class);
    private static final List<String> EXCLUDED_KEYS = ImmutableList.of("service.pid", "felix.fileinstall.filename");

    public static final String DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH = "classpath://org/apache/brooklyn/core/mgmt/persist/deserializingClassRenames.properties";
    public static final String KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES = "org.apache.brooklyn.classrename";

    private static final List<Loader> loaders = Lists.newCopyOnWriteArrayList();
    static {
        loaders.add(new ClasspathConfigLoader());
    }
    
    private static volatile Map<String, String> cache;

    @Beta
    public static Map<String, String> loadDeserializingClassRenames() {
        synchronized (DeserializingClassRenamesProvider.class) {
            if (cache == null) {
                ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
                for (Loader loader : loaders) {
                    builder.putAll(loader.load());
                }
                cache = builder.build();
                LOG.info("Class-renames cache loaded, size {}", cache.size());
            }
            return cache;
        }
    }

    /**
     * Handles inner classes, where the outer class has been renamed. For example:
     * 
     * {@code findMappedName("com.example.MyFoo$MySub")} will return {@code com.example.renamed.MyFoo$MySub}, if
     * the renamed contains {@code com.example.MyFoo: com.example.renamed.MyFoo}.
     */
    @Beta
    public static String findMappedName(String name) {
        return Reflections.findMappedNameAndLog(DeserializingClassRenamesProvider.loadDeserializingClassRenames(), name);
    }

    public static void reset() {
        synchronized (DeserializingClassRenamesProvider.class) {
            cache = null;
        }
    }

    public interface Loader {
        public Map<String, String> load();
    }
    
    /**
     * Loads the class-renames from the OSGi configuration file: {@code org.apache.brooklyn.classrename.cfg}.
     * 
     * Only public for OSGi instantiation - treat as an internal class, which may change in 
     * future releases.
     * 
     * See http://stackoverflow.com/questions/18844987/creating-a-blueprint-bean-from-an-inner-class:
     * we unfortunately need to include {@code !org.apache.brooklyn.core.mgmt.persist.DeserializingClassRenamesProvider}
     * in the Import-Package, as the mvn plugin gets confused due to the use of this inner class
     * within the blueprint.xml.
     * 
     * @see {@link DeserializingClassRenamesProvider#KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES}
     */
    public static class OsgiConfigLoader implements Loader {
        private ConfigurationAdmin configAdmin;

        public OsgiConfigLoader() {
            LOG.trace("OsgiConfigLoader instance created");
        }

        // For injection as OSGi bean
        public void setConfigAdmin(ConfigurationAdmin configAdmin) {
            this.configAdmin = configAdmin;
        }

        // Called by OSGi
        public void init() {
            LOG.trace("DeserializingClassRenamesProvider.OsgiConfigLoader.init: registering loader");
            DeserializingClassRenamesProvider.loaders.add(this);
            DeserializingClassRenamesProvider.reset();
        }

        // Called by OSGi
        public void destroy() {
            LOG.trace("DeserializingClassRenamesProvider.OsgiConfigLoader.destroy: unregistering loader");
            boolean removed = DeserializingClassRenamesProvider.loaders.remove(this);
            if (removed) {
                DeserializingClassRenamesProvider.reset();
            }
        }

        // Called by OSGi when configuration changes
        public void updateProperties(Map properties) {
            LOG.debug("DeserializingClassRenamesProvider.OsgiConfigLoader.updateProperties: clearing cache, so class-renames will be reloaded");
            DeserializingClassRenamesProvider.reset();
        }

        @Override
        public Map<String, String> load() {
            if (configAdmin == null) {
                LOG.warn("No OSGi configuration-admin available - cannot load {}.cfg", KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES);
                return ImmutableMap.of();
            }
            
            String filter = '(' + Constants.SERVICE_PID + '=' + KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES + ')';
            Configuration[] configs;

            try {
                configs = configAdmin.listConfigurations(filter);
            } catch (InvalidSyntaxException | IOException e) {
                LOG.info("Cannot list OSGi configurations");
                throw Exceptions.propagate(e);
            }

            final MutableMap<String, String> map = MutableMap.of();
            if (configs != null) {
                for (Configuration config : configs) {
                    LOG.debug("Reading OSGi configuration from {}; bundleLocation={}", config.getPid(), config.getBundleLocation());
                    map.putAll(dictToMap(config.getProperties()));
                }
            } else {
                LOG.info("No OSGi configuration found for {}.cfg", KARAF_DESERIALIZING_CLASS_RENAMES_PROPERTIES);
            }

            return map;
        }
        
        private Map<String, String> dictToMap(Dictionary<String, Object> props) {
            Map<String, String> mapProps = MutableMap.of();
            Enumeration<String> keyEnum = props.keys();
            while (keyEnum.hasMoreElements()) {
                String key = keyEnum.nextElement();
                if (!EXCLUDED_KEYS.contains(key)) {
                    mapProps.put(key, (String) props.get(key));
                }
            }
            return mapProps;
        }
    }
    
    /**
     * Loads the class-renames from the configuration file on the classpath.
     * 
     * @see {@link DeserializingClassRenamesProvider#DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH}
     */
    protected static class ClasspathConfigLoader implements Loader {
        @Override
        public Map<String, String> load() {
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
                LOG.warn("Failed to load class-renames from " + DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH + " (continuing)", e);
                return ImmutableMap.<String, String>of();
            }
        }
    }
}
