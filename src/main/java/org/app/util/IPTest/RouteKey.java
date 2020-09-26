package org.app.util.IPTest;

import java.util.Objects;
public class RouteKey {
    private final String key;

    private RouteKey(String keyValue) {
        this.key = keyValue;
    }

    public static RouteKey key(String keyValue) {
        return new RouteKey(keyValue);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            RouteKey routeKey = (RouteKey) obj;
            return Objects.equals(this.key, routeKey.getKey());
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hashCode(this.key);
    }

    private String getKey() {
        return this.key;
    }
}