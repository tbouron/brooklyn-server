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
package org.apache.brooklyn.core.mgmt.rebind;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.objs.BrooklynObjectType;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.testng.annotations.Test;

public class RebindHistoricCatalogItemTest extends AbstractRebindHistoricTest {
    
    /**
     * Purpose of test is for rebinding to historic state created before we auto-wrapped .bom 
     * catalog items in bundles.
     * 
     * The persisted state contains a catalog item (which is not wrapped as a bundle).
     * It was created in brooklyn 0.11.0 via {@code br catalog add app.bom}, using:
     * <pre>
     *   brooklyn.catalog:
     *     id: catalog-myapp
     *     version: 1.0.0
     *     itemType: entity
     *     item:
     *       type: org.apache.brooklyn.entity.stock.BasicApplication
     * </pre>
     */
    @Test
    public void testLegacyCatalogItem() throws Exception {
        addMemento(BrooklynObjectType.CATALOG_ITEM, "catalog", "myapp_1.0.0");
        rebind();
        
        RegisteredType type = mgmt().getTypeRegistry().get("myapp", "1.0.0");
        assertEquals(type.getKind(), RegisteredTypeKind.SPEC);
    }
}
