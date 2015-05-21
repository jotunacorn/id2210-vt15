package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-04-24.
 */
public class KPing {

    private NatedAddress addressToPing;
    private int pingNr;

    public KPing(NatedAddress addressToPing, int pingNr) {
        this.addressToPing = addressToPing;
        this.pingNr = pingNr;
    }

    public NatedAddress getAddressToPing() {
        return addressToPing;
    }

    public void setAddressToPing(NatedAddress addressToPing) {
        this.addressToPing = addressToPing;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }
}
