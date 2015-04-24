package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-04-24.
 */
public class KPing {

    private NatedAddress addressToPing;

    public KPing(NatedAddress addressToPing) {
        this.addressToPing = addressToPing;
    }

    public NatedAddress getAddressToPing() {
        return addressToPing;
    }

    public void setAddressToPing(NatedAddress addressToPing) {
        this.addressToPing = addressToPing;
    }
}
