package se.kth.swim.timeout;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-04-21.
 */
public class SuspectedTimeout extends Timeout {

    private NatedAddress address;
    private int pingNr;

    public SuspectedTimeout(ScheduleTimeout request, NatedAddress address, int pingNr) {
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