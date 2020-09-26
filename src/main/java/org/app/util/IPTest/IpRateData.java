package org.app.util.IPTest;
import java.util.*;
public class IpRateData {
    private String ip;
    private long rate;
    public IpRateData(String ipValue,long rateValue){
        this.ip=ipValue;
        this.rate = rateValue;
    }
    public String getIp(){
        return ip;
    }
    public long getRate(){
        return rate;
    }
}