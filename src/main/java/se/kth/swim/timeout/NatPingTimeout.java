package se.kth.swim.timeout;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-05-17.
 */
public class NatPingTimeout extends Timeout {

    private NatedAddress address;
    private int pingNr;

    public NatPingTimeout(ScheduleTimeout request, NatedAddress address, int pingNr) {
        super(request);
        this.pingNr = pingNr;
        this.address = address;
    }

    public int getPingNr() {
        return pingNr;
    }

    public NatedAddress getAddress() {
        return address;
    }
}
