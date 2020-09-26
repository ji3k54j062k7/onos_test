package org.app.util.IPTest;

import java.util.ArrayList;

public interface IpRouteFeatures {
    ArrayList<String> getIpRoute();
    long getRate();
    public interface Builder {
        IpRouteFeatures.Builder setSFCIpRoute(ArrayList<String> ipRouteData);
        IpRouteFeatures.Builder setSFCRate(long rate);

        IpRouteFeatures build();
    };
}