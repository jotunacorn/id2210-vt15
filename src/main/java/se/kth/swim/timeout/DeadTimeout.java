package se.kth.swim.timeout;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-05-21.
 */
public class DeadTimeout extends Timeout {

    private NatedAddress address;
    private int pingNr;

    public DeadTimeout(ScheduleTimeout request, NatedAddress address, int pingNr) {
        super(request);

        this.address = address;
        this.pingNr = pingNr;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }

}