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
package org.apache.brooklyn.core.server.entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestApplicationNoEnrichersImpl;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityNoEnrichersImpl;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class BrooklynMetricsTest extends BrooklynAppUnitTestSupport {

    private final static int NUM_SUBSCRIPTIONS_PER_ENTITY = 4;
    
    TestApplication app;
    SimulatedLocation loc;
    BrooklynMetrics brooklynMetrics;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newSimulatedLocation();
        brooklynMetrics = app.addChild(EntitySpec.create(BrooklynMetrics.class).configure("updatePeriod", 10L));
    }
    
    @Override
    protected void setUpApp() {
        app = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class)
                .impl(TestApplicationNoEnrichersImpl.class));
    }
    
    @Test
    public void testInitialBrooklynMetrics() {
        app.start(ImmutableList.of(loc));

        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.FIVE_SECONDS), new Runnable() {
            @Override
            public void run() {
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), (Long)1L);
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_TASKS_SUBMITTED) > 0);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_INCOMPLETE_TASKS), (Long)0L);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_ACTIVE_TASKS), (Long)0L);
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_PUBLISHED) > 0);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_DELIVERED), (Long)0L);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_SUBSCRIPTIONS), (Long)(0L+NUM_SUBSCRIPTIONS_PER_ENTITY));
            }});
    }
    
    @Test
    public void testBrooklynMetricsIncremented() {
        TestEntity e = app.createAndManageChild(EntitySpec.create(TestEntity.class, TestEntityNoEnrichersImpl.class));
        app.start(ImmutableList.of(loc));

        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.FIVE_SECONDS), new Runnable() {
            @Override
            public void run() {
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), (Long)2L); // for app and testEntity's start
            }});

        // Note if attribute has not yet been set, the value returned could be null
        final long effsInvoked = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EFFECTORS_INVOKED, 0);
        final long tasksSubmitted = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_TASKS_SUBMITTED, 0);
        final long eventsPublished = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EVENTS_PUBLISHED, 0);
        final long eventsDelivered = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EVENTS_DELIVERED, 0);

        // Invoking an effector increments effector/task count
        e.myEffector();
        
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), (Long)(effsInvoked+1));
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_TASKS_SUBMITTED) > tasksSubmitted);
            }});
        
        // Setting attribute causes event to be published and delivered to the subscriber
        // Note that the brooklyn metrics entity itself is also publishing sensors
        app.subscriptions().subscribe(e, TestEntity.SEQUENCE, SensorEventListener.NOOP);
        e.sensors().set(TestEntity.SEQUENCE, 1);
        
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.FIVE_SECONDS), new Runnable() {
            @Override
            public void run() {
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_PUBLISHED) > eventsPublished);
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_DELIVERED) > eventsDelivered);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_SUBSCRIPTIONS), (Long)
                    (1L + NUM_SUBSCRIPTIONS_PER_ENTITY));
            }});
    }
    
    private long getAttribute(Entity entity, AttributeSensor<Long> attribute, long defaultVal) {
        Long result = entity.getAttribute(attribute);
        return (result != null) ? result : defaultVal;
    }
}
