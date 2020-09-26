package org.app;


import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.onlab.packet.*;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.GroupId;
import org.onosproject.net.*;
import org.onosproject.net.behaviour.ExtensionTreatmentResolver;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.instructions.ExtensionPropertyException;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatmentType;
import org.onosproject.net.group.*;
import org.onosproject.net.host.*;
import org.onosproject.net.meter.*;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SFCWorking implements PacketProcessor {
    private FlowRuleService flowRuleService;
    private HostService hostService;
    private DeviceService deviceService;
    private TopologyService topologyService;
    private GroupService groupService;
    private ApplicationId appId;
    private MeterService meterService;
    private MeterStore meterStore;
    private ARPHandler arpHandler;
    private PacketService packetService;
    private ArrayList<String> ipArrayList;
    private Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();
    Map<MacAddress, PortNumber> macTable;
    private ArrayList<MacAddress> macAddressList;
    private Map<IpAddress, MacAddress> sfcList = Maps.newConcurrentMap();
    // private ArrayList<MacAddress> prescribedRouteList;
    private int Temporary = 5;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static KryoNamespace appKryo = new KryoNamespace.Builder()
            .register(Integer.class)
            .register(DeviceId.class)
            .build("group-fwd-app");

    public SFCWorking(FlowRuleService flowRuleService, ApplicationId appId, HostService hostService,
                      DeviceService deviceService, TopologyService topologyService,
                      GroupService groupService, MeterService meterService, MeterStore meterStore, PacketService packetService) {
        this.flowRuleService = flowRuleService;
        this.hostService = hostService;
        this.deviceService = deviceService;
        this.topologyService = topologyService;
        this.groupService = groupService;
        this.meterService = meterService;
        this.meterStore = meterStore;
        this.appId = appId;
        this.hostService.addListener(hostListener);
        this.packetService = packetService;
        arpHandler = new ARPHandler();
        Iterator<Device> deviceIterator = deviceService.getAvailableDevices().iterator();
        ipArrayList = new ArrayList<String>();
        macAddressList = new ArrayList<MacAddress>();
        // prescribedRouteList=new ArrayList<MacAddress>();
        ipArrayList.add("10.0.0.1");
        ipArrayList.add("10.0.0.2");
        ipArrayList.add("10.0.0.3");
        Device device = deviceIterator.next();
        for (String ipValue : ipArrayList) {
            arpHandler.buildArpRequest(packetService, device.id(), IpAddress.valueOf(ipValue));
        }
    }

    private HostListener hostListener = event -> {
        MacAddress macAddress = event.subject().mac();
        IpAddress ipv4Address = (IpAddress) event.subject().ipAddresses().toArray()[0];
        String[] subject_info = event.subject().location().toString().split("/");
        String deviceId = subject_info[0];
        PortNumber port = PortNumber.fromString(subject_info[1]);
        if (event.type() == HostEvent.Type.HOST_ADDED || event.type() == HostEvent.Type.HOST_UPDATED) {
            macTables.putIfAbsent(DeviceId.deviceId(deviceId), Maps.newConcurrentMap());
            Map<MacAddress, PortNumber> macTable = macTables.get(DeviceId.deviceId(deviceId));
            macAddressList.add(macAddress);
            sfcList.put(ipv4Address, macAddress);
            log.info("sfcList_body:" + sfcList);
            macTable.put(macAddress, port);
            
            //    dnsIpAddressHostInfoMap.put(ipv4Address.toString(), macAddress);
            //            log.info("ipAddressHostInfoMap:" + ipAddressHostInfoMap + "\n" +
            //                    "info: " + info + "\n" +
            //                    "dnsIpAddressHostInfoMap:  " + dnsIpAddressHostInfoMap);
        } else if (event.type() == HostEvent.Type.HOST_REMOVED) {
            //     macTables.remove(deviceId);
            //    dnsIpAddressHostInfoMap.remove(ipv4Address.toString());
        }
    };

    @Override
    public void process(PacketContext packetContext) {
        log.info("------------------------------------------------------");
        initMacTable(packetContext.inPacket().receivedFrom());
        // registerMeter(packetContext);
        actLikeSwitchforSFC(packetContext);
        runSFC(packetContext);
        // processSFC(packetContext);
    }
    private void registerMeter(PacketContext packetContext) {
        DeviceId deviceId = packetContext.inPacket().receivedFrom().deviceId();
        long maxMeters = meterStore.getMaxMeters(MeterFeaturesKey.key(deviceId));
        if (0L == maxMeters) {
            meterStore.storeMeterFeatures(DefaultMeterFeatures.builder()
                    .forDevice(packetContext.inPacket().receivedFrom().deviceId())
                    .withMaxMeters(1000L)
                    .build());
        }
    }
    private void actLikeSwitchforSFC(PacketContext packetContext) {
        Ethernet ethernet = packetContext.inPacket().parsed();
        log.info("actLikeSwitchforSFC----------------" + ethernet.getEtherType());

        short type = ethernet.getEtherType();

        if (type != Ethernet.TYPE_IPV4 && type != Ethernet.TYPE_ARP) {
            return;
        }
        ConnectPoint connectPoint = packetContext.inPacket().receivedFrom();
        Map<MacAddress, PortNumber> macTable = macTables.get(connectPoint.deviceId());
        MacAddress srcMac = ethernet.getSourceMAC();

        MacAddress dstMac = ethernet.getDestinationMAC();
        macTable.put(srcMac, connectPoint.port());
        PortNumber outPort = macTable.get(dstMac);

        if (outPort != null) {
            if (type == Ethernet.TYPE_IPV4) {
                IPv4 iPv4 = (IPv4) ethernet.getPayload();

                TrafficSelector trafficSelector = DefaultTrafficSelector.builder()
                        .matchEthSrc(srcMac)
                        .matchEthDst(dstMac)
                        .build();
                createFlowRule(trafficSelector,
                        createTrafficTreatment(null, outPort),
                        connectPoint.deviceId());

            } else {
                packetContext.treatmentBuilder().setOutput(outPort);
                packetContext.send();
            }
        } else {
            actLikeHub(packetContext);
        }
    }

    private void createFlowRule(TrafficSelector trafficSelector, TrafficTreatment trafficTreatment, DeviceId deviceId) {
        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(trafficSelector)
                .withTreatment(trafficTreatment)
                .forDevice(deviceId)
                .withPriority(10)
                .makeTemporary(Temporary)
                .build();

        flowRuleService.applyFlowRules(flowRule);
    }
    private void runSFC(PacketContext packetContext) {
        if (packetContext.inPacket().parsed().getEtherType() != Ethernet.TYPE_IPV4) return;
        
        ArrayList<String> ipArrayListforReturnRoute = new ArrayList<>(ipArrayList);
        Collections.reverse(ipArrayListforReturnRoute);


        addTag(flowRuleService, packetContext, ipArrayList, false);
    

    }

    //改封包
    private void addTag(FlowRuleService flowRuleService, PacketContext packetContext, ArrayList<String> ipArrayListforAddTag, boolean isReverse) {
        new Thread(() -> {
            Ethernet ethernet = packetContext.inPacket().parsed();
            IPv4 iPv4 = (IPv4) ethernet.getPayload();
            int sfId = 0;
            log.info("ipArrayListforAddTag:"+ipArrayListforAddTag);
            for (int position = 0; position < ipArrayListforAddTag.size() - 1; position++) {
                String currentIp = ipArrayListforAddTag.get(position);
                String nextIp = ipArrayListforAddTag.get(position + 1);
                MplsLabel mplsLabel;
                MacAddress ethDst;
                IpPrefix iPDst;
                if (isReverse) {
                    mplsLabel = MplsLabel.mplsLabel(1048575 - sfId);
                    ethDst = ethernet.getSourceMAC();
                    IpAddress dstIpAddress = IpAddress.valueOf(iPv4.getDestinationAddress());
                    iPDst = dstIpAddress.toIpPrefix();
                } else {
                    mplsLabel = MplsLabel.mplsLabel(sfId);
                    ethDst = ethernet.getDestinationMAC();
                    IpAddress dstIpAddress = IpAddress.valueOf(iPv4.getDestinationAddress());
                    iPDst = dstIpAddress.toIpPrefix();
                }
                //currentIpAddress
                IpAddress ipAddress = IpAddress.valueOf(currentIp);

                TrafficTreatment trafficTreatment = DefaultTrafficTreatment.builder()
                        .pushMpls()
                        .setMpls(mplsLabel)
                        .transition(1)
                        .build();


                FlowRule flowRule = DefaultFlowRule.builder()
                        .withSelector(DefaultTrafficSelector.builder()
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchEthDst(ethDst)
                                .matchEthSrc(sfcList.get(ipAddress))
                                .matchIPDst(iPDst)
                                .build())
                        .withTreatment(trafficTreatment)
                        .forDevice(packetContext.inPacket().receivedFrom().deviceId())
                        .fromApp(AppComponent.appId)
                        .makeTemporary(Temporary)
                        .withPriority(50)
                        .forTable(0)
                        .build();
                log.info("-----addTagaddTagaddTag------" + flowRule.toString());
                flowRuleService.applyFlowRules(flowRule);

                processSfcFlowRule(packetContext, IpAddress.valueOf(currentIp), IpAddress.valueOf(nextIp), mplsLabel, isReverse, sfId);
                sfId++;

            }
        }).start();
    }

    private TrafficTreatment createTrafficTreatment(MeterId meterId, PortNumber outPort) {
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        if (null != meterId) trafficTreatment.meter(meterId);
        if (null != outPort) trafficTreatment.setOutput(outPort);
        return trafficTreatment.build();
    }

    private void initMacTable(ConnectPoint connectPoint) {
        macTables.putIfAbsent(connectPoint.deviceId(), Maps.newConcurrentMap());
    }

    private void actLikeHub(PacketContext packetContext) {
        packetContext.treatmentBuilder().setOutput(PortNumber.FLOOD);
        packetContext.send();
    }

    /**
     * 如果輸入封包符合就根據SFC轉發
     * 否則以一般轉發方式
     * <p>
     * 產生SFC假資料
     * 格式{sf1,sf2,sf3}
     **/
    /**
     * 處理SFC邏輯
     **/

    private void processSfcFlowRule(PacketContext context, IpAddress currentIP, IpAddress nextIp,
                                    MplsLabel mplsLabel, boolean isReverse, int sfId) {
        DeviceId currentDeviceId = context.inPacket().receivedFrom().deviceId();
        //取得DNS對應domain、chainId、sfInfo
        int groupId = generateGroupId(sfId, isReverse);

        processDeviceGroups(groupId, nextIp);

        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(DefaultTrafficSelector.builder()
                        .matchMplsLabel(mplsLabel)
                        .matchEthType(Ethernet.MPLS_UNICAST)
                        .build())
                .withTreatment(DefaultTrafficTreatment.builder()
                        .group(GroupId.valueOf(groupId))
                        .build())
                .forDevice(currentDeviceId)
                .fromApp(appId)
                .makeTemporary(Temporary)
                .withPriority(500)
                .forTable(1)
                .build();
        flowRuleService.applyFlowRules(flowRule);
        log.info("-----applyFlowRulesapplyFlowRules------" + flowRule.toString());

        //取得全部nextDomain
        MacAddress macAddress = sfcList.get(nextIp);
        Map<MacAddress, PortNumber> macTable = macTables.get(currentDeviceId);
        PortNumber outPort = macTable.get(macAddress);

//            log.info("mac {}\ntargetDeviceId  {} \ntargetPortnumber {} \ncurrentDeviceid {}\npaths {}\nhostDst.location().port() {}\n",
//                    macAddress,
//                    AppComponent.getIpAddressHostInfoMap().get(pickIpaddress(hostDst.ipAddresses(), macAddress)).getDeviceId(),
//                    AppComponent.getIpAddressHostInfoMap().get(pickIpaddress(hostDst.ipAddresses(), macAddress)).getPortNumber(),
//                    currentDeviceId,
//                    paths,
//                    hostDst.location().port());

        TrafficTreatment trafficTreatment = DefaultTrafficTreatment.builder()
                .popMpls()
                .setOutput(outPort)
                .build();

        FlowRule outputFlowRule = DefaultFlowRule.builder()
                .forTable(2)
                .makeTemporary(Temporary)
                .withPriority(500)
                .fromApp(appId)
                .forDevice(currentDeviceId)
                .withSelector(DefaultTrafficSelector.builder()
                        .matchMplsLabel(mplsLabel)
                        .matchEthType(Ethernet.MPLS_UNICAST)
                        .matchEthDst(macAddress)
                        .build())
                .withTreatment(trafficTreatment)
                .build();
        flowRuleService.applyFlowRules(outputFlowRule);
    }

    private int generateGroupId(int sfId, boolean isReverse) {
        return (String.valueOf(sfId) + isReverse).hashCode();
    }

    /**
     * 處理 Group Table
     * 傳入 domain 以及device ID
     **/

    private void processDeviceGroups(int finalGroupId, IpAddress nextIp) {
        Set<Device> devices = Sets.newHashSet(deviceService.getAvailableDevices());
        devices.forEach(targetDevice -> {
            ArrayList<GroupBucket> deviceBucket = createBucketForDevice(nextIp, targetDevice);
            GroupKey targetDeviceGroupKey = generateGroupKey(targetDevice.id(), finalGroupId);
            if (!groupExist(targetDevice, targetDeviceGroupKey)) {
                // 建立 GroupDescription 用來建立Group Table Action Bucket
                GroupDescription groupDescription = new DefaultGroupDescription(
                        targetDevice.id(),
                        GroupDescription.Type.SELECT,
                        new GroupBuckets(deviceBucket),
                        targetDeviceGroupKey,
                        finalGroupId,
                        appId);
                AppComponent.getDescriptionArrayList().add(groupDescription);
                // 建立Group Table Action Bucket
                groupService.addGroup(groupDescription);
            }
        });
    }

    // 建立Action Buckets mod eht_dst,send table 2
    private ArrayList<GroupBucket> createBucketForDevice(IpAddress ipAddress, Device device) {
        ArrayList<GroupBucket> bucketArrayList = new ArrayList<>();
        ExtensionTreatmentResolver resolver = device.as(ExtensionTreatmentResolver.class);
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        ExtensionTreatment extension = resolver.getExtensionInstruction(ExtensionTreatmentType.ExtensionTreatmentTypes.NICIRA_RESUBMIT_TABLE.type());
        try {
            extension.setPropertyValue("table", (short) 2);
            treatmentBuilder
                    .setEthDst(sfcList.get(ipAddress))
                    .extension(extension, device.id());
            bucketArrayList.add(DefaultGroupBucket.createSelectGroupBucket(treatmentBuilder.build()));

        } catch (ExtensionPropertyException e) {
            e.printStackTrace();
        }
        return bucketArrayList;
    }

    private GroupKey generateGroupKey(DeviceId deviceId, Integer groupId) {
        int hashed = Objects.hash(deviceId, groupId);
        return new DefaultGroupKey(appKryo.serialize(hashed));
    }

    private boolean groupExist(Device device, GroupKey groupKey) {
        return groupService.getGroup(device.id(), groupKey) != null;
    }
    private MeterId checkMeter(IpAddress ipv4Address,DeviceId deviceId) {
        for (String ipString:ipArrayList){
            if (ipString.equals(ipv4Address.toString())){
                IpAddress ip =IpAddress.valueOf(ipString);
                MeterId meterId = processMeterTable(deviceId,1000L);
                log.info("---------------checkMeter:"+meterId.toString());
                return meterId;
            }
        }
        return null;
        
    }
    private MeterId processMeterTable(DeviceId deviceId, long rate) {
        MeterId id = checkExistMeter(rate);
        if (null == id) {
            Set<Band> bands = new HashSet<>();
            bands.add(DefaultBand.builder()
                    .ofType(Band.Type.DROP)
                    .withRate(rate)
                    .burstSize(0)
                    .build());
            log.info("----------------processMeterTableBand:"+bands);
            MeterRequest meterRequest = DefaultMeterRequest.builder()
                    .forDevice(deviceId)
                    .fromApp(appId)
                    .withUnit(Meter.Unit.KB_PER_SEC)
                    .withBands(bands)
                    .add();
            return meterService.submit(meterRequest).id();
        } else {
            return id;
        }
    }
    private MeterId checkExistMeter(long rate) {
        boolean hasMeter = false;
        for (Meter meter : meterService.getAllMeters()) {
            log.info("---------------checkExistMeter:"+meter);
            for (Band band : meter.bands()) {
                if (rate == band.rate()) {
                    hasMeter = true;
                    break;
                }
            }
            if (hasMeter) {
                return meter.id();
            }
        }
        return null;
    }
}