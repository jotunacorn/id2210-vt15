package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-04-24.
 */
public class KPong {

    private NatedAddress address;
    private int incarnationCounter;

    public KPong(NatedAddress address, int incarnationCounter) {
        this.address = address;
        this.incarnationCounter = incarnationCounter;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }

    public void setIncarnationCounter(int incarnationCounter) {
        this.incarnationCounter = incarnationCounter;
    }
}
