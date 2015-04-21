package se.kth.swim.timeouts;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-04-21.
 */
public class SuspectedTimeout extends Timeout {

    private NatedAddress address;

    public SuspectedTimeout(ScheduleTimeout request, NatedAddress address) {
        super(request);

        this.address = address;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
    }
}