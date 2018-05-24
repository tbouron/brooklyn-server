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
package org.apache.brooklyn.util.internal;

/** 
 * Convenience for retrieving well-defined system properties, including checking if they have been set etc.
 */
public class BrooklynSystemProperties {

    // FIXME not used
    public static BooleanSystemProperty DEBUG = new BooleanSystemProperty("brooklyn.debug");
    // FIXME not used
    public static BooleanSystemProperty EXPERIMENTAL = new BooleanSystemProperty("brooklyn.experimental");
    
    // FIXME not used
    /** controls how long jsch delays between commands it issues */
    // -Dbrooklyn.jsch.exec.delay=100
    public static IntegerSystemProperty JSCH_EXEC_DELAY = new IntegerSystemProperty("brooklyn.jsch.exec.delay");

    /** allows specifying a particular geo lookup service (to lookup IP addresses), as the class FQN to use */
    // -Dorg.apache.brooklyn.core.brooklyn.location.geo.HostGeoLookup=org.apache.brooklyn.core.brooklyn.location.geo.UtraceHostGeoLookup
    public static StringSystemProperty HOST_GEO_LOOKUP_IMPL_LEGACY = new StringSystemProperty("brooklyn.location.geo.HostGeoLookup");
    public static StringSystemProperty HOST_GEO_LOOKUP_IMPL = new StringSystemProperty("org.apache.brooklyn.core.location.geo.HostGeoLookup");

    /** Allows the use of YAML tags to create arbitrary types known to Java. */
    public static BooleanSystemProperty YAML_TYPE_INSTANTIATION = new BooleanSystemProperty("org.apache.brooklyn.unsafe.YamlTypeInstantiation");
    
    /** Since 1.0.0 we no longer ask jclouds to authorizePublicKey for data in extraSshPublicKeyData; we do this ourselves, and the jclouds behaviour
     * interferes with the use of key pairs.
     * <p> 
     * Set -Dbrooklyn.jclouds.authorizePublicKey.extraSshPublicKeyData=true to enable the jclouds call and revert to previous behaviour.
     * 
     * @deprecated since introduction in 1.0.0, to be removed in 1.1 or later unless there is a need for this
     */
    @Deprecated
    public static BooleanSystemProperty JCLOUDS_AUTHORIZE_EXTRA_SSH_PUBLIC_KEY_DATA = new BooleanSystemProperty("brooklyn.jclouds.authorizePublicKey.extraSshPublicKeyData");
    
}
