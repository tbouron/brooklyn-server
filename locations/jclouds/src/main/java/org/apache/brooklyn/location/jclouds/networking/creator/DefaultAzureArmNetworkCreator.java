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
package org.apache.brooklyn.location.jclouds.networking.creator;

import static org.apache.brooklyn.core.location.cloud.CloudLocationConfig.CLOUD_REGION_ID;
import static org.apache.brooklyn.location.jclouds.api.JcloudsLocationConfigPublic.NETWORK_NAME;
import static org.apache.brooklyn.location.jclouds.api.JcloudsLocationConfigPublic.TEMPLATE_OPTIONS;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import org.jclouds.azurecompute.arm.AzureComputeApi;
import org.jclouds.azurecompute.arm.domain.ResourceGroup;
import org.jclouds.azurecompute.arm.domain.Subnet;
import org.jclouds.azurecompute.arm.domain.VirtualNetwork;
import org.jclouds.compute.ComputeService;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;

public class DefaultAzureArmNetworkCreator {

    public static final Logger LOG = LoggerFactory.getLogger(DefaultAzureArmNetworkCreator.class);

    private static final String DEFAULT_RESOURCE_GROUP = "brooklyn-default-resource-group";
    private static final String DEFAULT_NETWORK_NAME = "brooklyn-default-network";
    private static final String DEFAULT_SUBNET_NAME = "brooklyn-default-subnet";

    private static final String DEFAULT_VNET_ADDRESS_PREFIX = "10.1.0.0/16";
    private static final String DEFAULT_SUBNET_ADDRESS_PREFIX = "10.1.0.0/24";

    public static ConfigKey<Boolean> AZURE_ARM_DEFAULT_NETWORK_ENABLED = ConfigKeys.newBooleanConfigKey(
            "azure.arm.default.network.enabled",
            "When set to true, AMP will create a default network and subnet per Azure region and " +
                    "deploy applications there (if no network configuration has been set for the application).",
            true);

    public static void createDefaultNetworkAndAddToTemplateOptionsIfRequired(ComputeService computeService, ConfigBag config) {
        if (!config.get(AZURE_ARM_DEFAULT_NETWORK_ENABLED)) {
            LOG.info("azure.arm.default.network.enabled is disabled, not creating default network");
            return;
        }


        Map<String, Object> templateOptions = config.get(TEMPLATE_OPTIONS);

        //Only create a default network if we haven't specified a network name (in template options or config) or ip options
        if (config.containsKey(NETWORK_NAME)) {
            LOG.info("Network config specified when provisioning Azure machine. Not creating default network");
            return;
        }
        if (templateOptions != null && (templateOptions.containsKey(NETWORK_NAME.getName()) || templateOptions.containsKey("ipOptions"))) {
            LOG.info("Network config specified when provisioning Azure machine. Not creating default network");
            return;
        }

        LOG.info("Network config not specified when provisioning Azure machine. Creating default network if doesn't exist");

        AzureComputeApi api = computeService.getContext().unwrapApi(AzureComputeApi.class);
        String location = config.get(CLOUD_REGION_ID);

        String resourceGroupName = DEFAULT_RESOURCE_GROUP  + "-" + location;
        String vnetName = DEFAULT_NETWORK_NAME + "-" + location;
        String subnetName = DEFAULT_SUBNET_NAME + "-" + location;

        //Check if default already exists
        Subnet preexistingSubnet = api.getSubnetApi(resourceGroupName, vnetName).get(subnetName);
        if(preexistingSubnet != null){
            LOG.info("Default Azure network and subnet already created, "+vnetName);
            updateTemplateOptions(config, preexistingSubnet);
            return;
        }


        LOG.info("Network config not specified when creating Azure location and default network/subnet does not exists. Creating");

        createResourceGroupIfNeeded(api, resourceGroupName, location);

        //Setup properties for creating subnet/network
        Subnet subnet = Subnet.create(subnetName, null, null,
                Subnet.SubnetProperties.builder().addressPrefix(DEFAULT_SUBNET_ADDRESS_PREFIX).build());

        VirtualNetwork.VirtualNetworkProperties virtualNetworkProperties = VirtualNetwork.VirtualNetworkProperties
                .builder().addressSpace(VirtualNetwork.AddressSpace.create(Arrays.asList(DEFAULT_VNET_ADDRESS_PREFIX)))
                .subnets(Arrays.asList(subnet)).build();

        //Create network
        api.getVirtualNetworkApi(resourceGroupName).createOrUpdate(vnetName, location, virtualNetworkProperties);
        Subnet createdSubnet = api.getSubnetApi(resourceGroupName, vnetName).get(subnetName);

        //Add config
        updateTemplateOptions(config, createdSubnet);

    }

    private static void updateTemplateOptions(ConfigBag config, Subnet createdSubnet){
        Map<String, Object> templateOptions;

        if(config.containsKey(TEMPLATE_OPTIONS)) {
            templateOptions = MutableMap.copyOf(config.get(TEMPLATE_OPTIONS));
        } else {
            templateOptions = new HashMap<>();
        }

        templateOptions.put("ipOptions", ImmutableMap.of(
                "allocateNewPublicIp", true, //jclouds will not provide a public IP unless we set this
                "subnet", createdSubnet.id()
        ));

        config.put(TEMPLATE_OPTIONS, templateOptions);

    }

    private static void createResourceGroupIfNeeded(AzureComputeApi api, String resourceGroup, String location) {
        LOG.debug("using resource group [%s]", resourceGroup);
        ResourceGroup rg = api.getResourceGroupApi().get(resourceGroup);
        if (rg == null) {
            LOG.debug("resource group [%s] does not exist. Creating!", resourceGroup);
            api.getResourceGroupApi().create(resourceGroup, location,
                    ImmutableMap.of("description", "brooklyn default resource group"));
        }
    }
}
