package se.kth.swim.timeouts;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * Created by Mattias on 2015-05-17.
 */
public class HeartbeatTimeout extends Timeout {

    public HeartbeatTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}
