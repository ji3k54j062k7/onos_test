package org.app.util.sfc;

public interface ClassifierFeatures {
    String sourceDomain();

    String destinationDomain();

    long rate();

    void setRate(long rate);

    public interface Builder {
        ClassifierFeatures.Builder withSourceDomain(String sourceDomain);

        ClassifierFeatures.Builder withDestinationDomain(String destinationDomain);

        ClassifierFeatures.Builder withRate(long rate);

        ClassifierFeatures build();
    }
}
