package se.kth.swim.timeout;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * Created by Mattias on 2015-04-21.
 */
public class PingTimeout extends Timeout {

    public PingTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}