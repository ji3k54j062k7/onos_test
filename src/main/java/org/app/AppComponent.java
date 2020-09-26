/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.app;
import com.google.common.collect.Maps;
import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.device.*;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.meter.MeterService;
import org.onosproject.net.meter.MeterStore;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import org.app.SFCWorking;
import org.app.util.sf.SFFeatures;
import org.app.util.sf.SFKey;
import org.app.util.sfc.SFCFeatures;
import org.app.util.sfc.SFCKey;
import org.app.util.IPTest.IpRouteFeatures;
import org.app.util.IPTest.RouteKey;
/**
 * Tutorial class used to help build a basic onos learning switch application.
 * This class contains the solution to the learning switch tutorial.  Change "enabled = false"
 * to "enabled = true" below, to run the solution.
 */
@Component(immediate = true)
public class AppComponent {
    // Instantiates the relevant services.
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MeterService meterService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MeterStore meterStore;
    private final Logger log = LoggerFactory.getLogger(getClass());

    /*
     * Defining macTables as a concurrent map allows multiple threads and packets to
     * use the map without an issue.
     */
    public static ApplicationId appId;
    private PacketProcessor processor;
    private int count=0;
    private SFCWorking mSFCWorking;
    private static Map<SFCKey, SFCFeatures> sfcInfo = Maps.newConcurrentMap();
    private static Map<SFKey, SFFeatures> sfInfo = Maps.newConcurrentMap();
    private static ArrayList<GroupDescription> descriptionArrayList = new ArrayList<>();
    private static Map<RouteKey,IpRouteFeatures> ipToSFCInfo = Maps.newConcurrentMap();

    /**
     * Create a variable of the SwitchPacketProcessor class using the PacketProcessor defined above.
     * Activates the app.
     */
    @Activate
    protected void activate() {
        log.info("Started============================");
        appId = coreService.getAppId("test123"); //equal to the name shown in pom.xml file

         // app id
         mSFCWorking = new SFCWorking(flowRuleService, appId, hostService,
                 deviceService, topologyService, groupService, meterService, meterStore,packetService);
         // add packet processor
         packetService.addProcessor(mSFCWorking, PacketProcessor.director(3));
         packetService.requestPackets(DefaultTrafficSelector.builder()
                 .matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());

    }

    /**
     * Deactivates the processor by removing it.
     */

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
        for (GroupDescription description : descriptionArrayList) {
            log.info("remove {} ", description);
            groupService.removeGroup(description.deviceId(), description.appCookie(), appId);
        }
        flowRuleService.removeFlowRulesById(AppComponent.appId);
        packetService.removeProcessor(mSFCWorking);
    }

    public static Map<SFKey, SFFeatures> getSfInfo() {
        return sfInfo;
    }

    public static Map<SFCKey, SFCFeatures> getSfc() {
        return sfcInfo;
    }

    public static ArrayList<GroupDescription> getDescriptionArrayList() {
        return descriptionArrayList;
    }
    public static Map<RouteKey,IpRouteFeatures> getIpRouteInfo() {
        return ipToSFCInfo;
    }
}