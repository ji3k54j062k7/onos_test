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
package org;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang.StringUtils;
import org.app.util.IPTest.IpRateData;
import org.app.util.IPTest.IpRoute;
import org.app.util.IPTest.IpRouteFeatures;
import org.app.util.IPTest.RouteKey;
import org.onlab.packet.IpAddress;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import static org.app.AppComponent.*;
import static org.onlab.util.Tools.readTreeFromStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Sample web resource.
 */
@Path("sfc")
public class AppWebResource extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Get hello world greeting.
     *
     * @return 200 OK
     */
    @GET
    @Path("")
    public Response getGreeting() {
        ObjectNode node = mapper().createObjectNode().put("hello", "world");
        return ok(node).build();
    }
    
    @POST
    @Path("register")
    @Produces(MediaType.APPLICATION_JSON) //表示輸出json
    @Consumes(MediaType.APPLICATION_JSON) //表示輸入為json 
    public Response getSfInfo(InputStream stream) {
        ObjectNode root = mapper().createObjectNode();
        try {
            ObjectNode jsonTree = readTreeFromStream(mapper(), stream);
            JsonNode inputData = jsonTree.get("inputData");
            if (inputData.isArray()) {
                for (JsonNode objNode : inputData) {
                    ArrayList<String> ipAddressArrayList = new ArrayList<>();
                    for (JsonNode ip : objNode.get("ipArray")) {
                        String ipv4Address = ip.asText();
                        ipAddressArrayList.add(ipv4Address);
                    }
                    long rate=Long.valueOf(objNode.get("rate").asText().toString());
                    String source = objNode.get("source").asText();
                    String destination = objNode.get("destination").asText();

                    IpRouteFeatures ipRouteFeatures = IpRoute.builder()
                        .setSFCRate(rate)
                        .setSFCIpRoute(ipAddressArrayList)
                        .build();
                    getIpRouteInfo().put(RouteKey.key(source+destination), ipRouteFeatures);
                    root.put("return","register Success");
                }
            }

        } catch (Exception e) {
            root.put("return",e.toString());
        }
        return ok(root).build();
    }
    @GET
    @Path("list")
    public Response listSF() {
        ObjectNode root = mapper().createObjectNode();
        ArrayNode arrayNode = root.putArray("IpRouteInfo");
        
        for (Map.Entry<RouteKey,IpRouteFeatures> entry : getIpRouteInfo().entrySet()) {
            ObjectNode infoData = mapper().createObjectNode();
            infoData.put("key",entry.getKey().toString());
            infoData.put("ipRoute",entry.getValue().getIpRoute().toString());
            infoData.put("rate",entry.getValue().getRate());
            arrayNode.add(infoData);
        }
        return ok(root).build();
    }
    @POST
    @Path("delete")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteSFC(InputStream stream) {
        ObjectNode root = mapper().createObjectNode();
        try {
            ObjectNode jsonTree = readTreeFromStream(mapper(), stream);
            // String routeKey = jsonTree.get("routeKey").asText();
            String source = jsonTree.get("source").asText();
            String destination = jsonTree.get("destination").asText();
            if (getIpRouteInfo().get(RouteKey.key(source+destination)) != null) {
                // getIpRouteInfo().remove(RouteKey.key(routeKey));
                root.put("Status", "Successful:");
                root.put("routeKey",source+destination);
            } else {
                root.put("Status", "not found sfc");
                return ok(root).status(403).build();
            }
        } catch (IOException e) {
            root.put("Status", "Fail");
            root.put("Message", e.getMessage());
        }
        return ok(root).build();
    }
}
