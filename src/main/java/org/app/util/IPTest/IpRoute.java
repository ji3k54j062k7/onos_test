package org.app.util.IPTest;
import com.google.common.base.Preconditions;
import java.util.*;
public class IpRoute implements IpRouteFeatures{
    private final ArrayList<String> ipRouteData;
    private final long rate ;
    public IpRoute(ArrayList<String> ipRouteValue,long rateValue) {
        this.ipRouteData=ipRouteValue;
        this.rate=rateValue;
    }
    @Override
    public ArrayList<String> getIpRoute() {
        return this.ipRouteData;
    }
    @Override
    public long getRate() {
        return this.rate;
    }
    public static IpRoute.Builder builder() {
        return new IpRoute.Builder();
    }

    public static final class Builder implements IpRouteFeatures.Builder {
        private ArrayList<String> ipRouteData;
        private long rate ;

        @Override
        public IpRouteFeatures.Builder setSFCRate(long rate) {
            this.rate = rate;
            return this;
        }

        @Override
        public IpRouteFeatures.Builder setSFCIpRoute(ArrayList<String> ipRouteData) {
            this.ipRouteData = ipRouteData;
            return this;
        }
        @Override
        public IpRouteFeatures build() {
            Preconditions.checkNotNull(this.ipRouteData, "Must specify a ipRoute for SFC");
            return new IpRoute(this.ipRouteData,rate);
        }
    }
}