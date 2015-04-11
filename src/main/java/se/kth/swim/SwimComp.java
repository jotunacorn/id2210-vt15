/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetAlive;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.*;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.*;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
@SuppressWarnings("FieldCanBeLocal")
public class SwimComp extends ComponentDefinition {

    private static final int PING_TIMEOUT = 10;

    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    private final Set<NatedAddress> bootstrapNodes;
    private final NatedAddress aggregatorAddress;

    private UUID pingTimeoutId;
    private UUID statusTimeoutId;

    private int sentPings = 0;
    private int receivedPings = 0;

    private Set<NatedAddress> aliveNodes;
    private Set<NatedAddress> suspectedNodes;
    private Set<NatedAddress> deadNodes;

    private List<Integer> sentPingNrs;

    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        this.bootstrapNodes = init.bootstrapNodes;
        this.aggregatorAddress = init.aggregatorAddress;

        aliveNodes = new HashSet<NatedAddress>();
        suspectedNodes = new HashSet<NatedAddress>();
        deadNodes = new HashSet<NatedAddress>();
        sentPingNrs = new ArrayList<Integer>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleAlive, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handlePongTimeout, timer);
        subscribe(handleSuspectedTimeout, timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress.getId()});

            if (!bootstrapNodes.isEmpty()) {
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }

    };

    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }
            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }

    };

    private Handler<NetPong> handlePong = new Handler<NetPong>() {

        @Override
        public void handle(NetPong event) {
            log.info("{} received pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});

            aliveNodes.add(event.getSource());
            aliveNodes.addAll(event.getContent().getAliveNodes());
            aliveNodes.remove(selfAddress);

            sentPingNrs.remove(Integer.valueOf(event.getContent().getPingNr()));

            boolean removeSuspectedNodeSucceeded = suspectedNodes.remove(event.getSource());
            boolean removeDeadNodeSucceeded = deadNodes.remove(event.getSource());

            if (removeSuspectedNodeSucceeded || removeDeadNodeSucceeded) {
                log.info("{} Restored suspected node: {}", new Object[]{selfAddress.getId(), event.getSource()});
            }

            if (event.getContent().getSuspectedNodes().contains(selfAddress) || event.getContent().getDeadNodes().contains(selfAddress)) {
                log.info("{} Found self in suspected or dead list from node: {}", new Object[]{selfAddress.getId(), event.getSource()});
                for (NatedAddress address : aliveNodes) {
                    trigger(new NetAlive(selfAddress, address), network);
                }
            }

            log.info("{} Alive nodes: {}", new Object[]{selfAddress.getId(), aliveNodes});
        }

    };

    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
            log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            receivedPings++;

            aliveNodes.add(event.getSource());

            log.info("{} sending pong to :{}", new Object[]{selfAddress.getId(), event.getSource()});

            if (Math.random() > 0.5) {
                trigger(new NetPong(selfAddress, event.getSource(), aliveNodes, suspectedNodes, deadNodes, event.getContent().getPingNr()), network);
            }
        }

    };

    private Handler<NetAlive> handleAlive = new Handler<NetAlive>() {

        @Override
        public void handle(NetAlive netAlive) {
            log.info("{} Restored suspected node by alive message from: {}", new Object[]{selfAddress.getId(), netAlive.getSource()});
            aliveNodes.add(netAlive.getSource());
            suspectedNodes.remove(netAlive.getSource());
            deadNodes.remove(netAlive.getSource());
        }
    };

    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
            for (NatedAddress partnerAddress : bootstrapNodes) {
                log.info("{} sending ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});

                trigger(new NetPing(selfAddress, partnerAddress, sentPings), network);

                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(PING_TIMEOUT);
                PongTimeout pongTimeout = new PongTimeout(scheduleTimeout, sentPings, partnerAddress);
                scheduleTimeout.setTimeoutEvent(pongTimeout);
                trigger(scheduleTimeout, timer);

                sentPingNrs.add(sentPings);

                sentPings++;
            }
        }

    };

    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            log.info("{} sending status to aggregator:{}", new Object[]{selfAddress.getId(), aggregatorAddress});
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(receivedPings)), network);
        }

    };

    private Handler<PongTimeout> handlePongTimeout = new Handler<PongTimeout>() {

        @Override
        public void handle(PongTimeout pongTimeout) {
            if (sentPingNrs.contains(pongTimeout.getPingNr())) {
                log.info("{} Suspected node: {}", new Object[]{selfAddress.getId(), pongTimeout.getAddress()});

                suspectedNodes.add(pongTimeout.getAddress());

                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(PING_TIMEOUT);
                SuspectedTimeout suspectedTimeout = new SuspectedTimeout(scheduleTimeout, pongTimeout.getAddress());
                scheduleTimeout.setTimeoutEvent(suspectedTimeout);
                trigger(scheduleTimeout, timer);

                log.info("{} Alive nodes: {}", new Object[]{selfAddress.getId(), aliveNodes});
            }
        }
    };

    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>() {

        @Override
        public void handle(SuspectedTimeout suspectedTimeout) {
            log.info("{} Suspected node timeout: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});

            if (suspectedNodes.contains(suspectedTimeout.getAddress())) {
                log.info("{} Declared node dead: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});

                aliveNodes.remove(suspectedTimeout.getAddress());
                suspectedNodes.remove(suspectedTimeout.getAddress());
                deadNodes.add(suspectedTimeout.getAddress());

                log.info("{} Alive nodes: {}", new Object[]{selfAddress.getId(), aliveNodes});
            }
        }
    };

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(10000, 10000);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }

    public static class SwimInit extends Init<SwimComp> {

        public final NatedAddress selfAddress;
        public final Set<NatedAddress> bootstrapNodes;
        public final NatedAddress aggregatorAddress;

        public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.aggregatorAddress = aggregatorAddress;
        }
    }

    private static class StatusTimeout extends Timeout {

        public StatusTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private static class PingTimeout extends Timeout {

        public PingTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private static class PongTimeout extends Timeout {

        private int pingNr;
        private NatedAddress address;

        public PongTimeout(ScheduleTimeout request, int pingNr, NatedAddress address) {
            super(request);

            this.pingNr = pingNr;
            this.address = address;
        }

        public int getPingNr() {
            return pingNr;
        }

        public void setPingNr(int pingNr) {
            this.pingNr = pingNr;
        }

        public NatedAddress getAddress() {
            return address;
        }

        public void setAddress(NatedAddress address) {
            this.address = address;
        }
    }

    private static class SuspectedTimeout extends Timeout {

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

}
