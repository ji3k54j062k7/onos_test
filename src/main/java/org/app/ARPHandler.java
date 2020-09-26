package org.app;

import org.onlab.packet.*;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class ARPHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());
    // build ARP Request
    public void buildArpRequest(PacketService packetService, DeviceId deviceId, IpAddress senderIp) {
        Ethernet ethReply = ARP.buildArpRequest(
                MacAddress.valueOf("10:00:00:00:00:00").toBytes(),
                IpAddress.valueOf("10.254.0.1").toOctets(),
                senderIp.toOctets(),//目標 ip 位址
                VlanId.NO_VID);
// 泛洪
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.FLOOD)
                .build();

        log.info(" ethReply " + ethReply);
        log.info(" treatment " + treatment);
        log.info(" deviceId " + deviceId);
//送出ARP
        packetService.emit(new DefaultOutboundPacket(
                deviceId,
                treatment,
                ByteBuffer.wrap(ethReply.serialize())));

        log.info(" packetService.emit");

    }

}