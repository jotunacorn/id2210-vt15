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
import se.kth.swim.msg.Pong;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetAlive;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.node.NodeHandler;
import se.kth.swim.timeouts.PingTimeout;
import se.kth.swim.timeouts.PongTimeout;
import se.kth.swim.timeouts.StatusTimeout;
import se.kth.swim.timeouts.SuspectedTimeout;
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

    private static final int PING_TIMEOUT = 1000;

    public static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    private final NatedAddress aggregatorAddress;

    private UUID pingTimeoutId;
    private UUID statusTimeoutId;

    private int sentPings = 0;
    private int receivedPings = 0;
    private int incarnationCounter = 0;

    private NodeHandler nodeHandler;

    private List<Integer> sentPingNrs;

    public SwimComp(SwimInit init) {
        log.info("{} initiating...", init.selfAddress);

        selfAddress = init.selfAddress;
        aggregatorAddress = init.aggregatorAddress;

        nodeHandler = new NodeHandler(selfAddress);
        sentPingNrs = new ArrayList<>();

        for (NatedAddress address : init.bootstrapNodes) {
            nodeHandler.addAlive(address, 0);
        }

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

            if (nodeHandler.getRandomAliveNode() != null) {
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

            sentPingNrs.remove(Integer.valueOf(event.getContent().getPingNr()));

            for (NatedAddress address : event.getContent().getNewNodes().keySet()) {
                nodeHandler.addAlive(address, event.getContent().getNewNodes().get(address));
            }

            for (NatedAddress address : event.getContent().getSuspectedNodes().keySet()) {
                nodeHandler.addSuspected(address, event.getContent().getSuspectedNodes().get(address));
            }

            for (NatedAddress address : event.getContent().getDeadNodes().keySet()) {
                nodeHandler.addDead(address, event.getContent().getDeadNodes().get(address));
            }

            nodeHandler.addAlive(event.getSource(), event.getContent().getIncarnationCounter());
            //log.info("{} Restored suspected node: {}", new Object[]{selfAddress.getId(), event.getSource()});

            if (event.getContent().getSuspectedNodes().containsKey(selfAddress) || event.getContent().getDeadNodes().containsKey(selfAddress)) {
                log.info("{} Found self in suspected or dead list from node: {}", new Object[]{selfAddress.getId(), event.getSource()});

                incarnationCounter++;

                for (NatedAddress address : nodeHandler.getAliveNodes().keySet()) {
                    trigger(new NetAlive(selfAddress, address, incarnationCounter), network);
                }
            }

            nodeHandler.printAliveNodes();
        }

    };

    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
            log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});

            receivedPings++;

            nodeHandler.addAlive(event.getSource(), event.getContent().getIncarnationCounter());

            if (Math.random() > 0.5) {
                log.info("{} sending pong to :{}", new Object[]{selfAddress.getId(), event.getSource()});
                Pong pong = nodeHandler.getPong(event.getContent().getPingNr(), incarnationCounter);
                trigger(new NetPong(selfAddress, event.getSource(), pong), network);
            }
        }

    };

    private Handler<NetAlive> handleAlive = new Handler<NetAlive>() {

        @Override
        public void handle(NetAlive netAlive) {
            log.info("{} Restored suspected node by alive message from: {}", new Object[]{selfAddress.getId(), netAlive.getSource()});

            nodeHandler.addAlive(netAlive.getSource(), netAlive.getContent().getIncarnationCounter());
        }
    };

    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
            NatedAddress partnerAddress = nodeHandler.getRandomAliveNode();

            if (partnerAddress != null) {
                log.info("{} sending ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});

                trigger(new NetPing(selfAddress, partnerAddress, sentPings, incarnationCounter), network);

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

                nodeHandler.addSuspected(pongTimeout.getAddress());

                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(PING_TIMEOUT);
                SuspectedTimeout suspectedTimeout = new SuspectedTimeout(scheduleTimeout, pongTimeout.getAddress());
                scheduleTimeout.setTimeoutEvent(suspectedTimeout);
                trigger(scheduleTimeout, timer);

                nodeHandler.printAliveNodes();
            }
        }
    };

    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>() {

        @Override
        public void handle(SuspectedTimeout suspectedTimeout) {
            //log.info("{} Suspected node timeout: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});

            if (nodeHandler.addDead(suspectedTimeout.getAddress())) {
                log.info("{} Declared node dead: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});

                nodeHandler.printAliveNodes();
            }
        }
    };

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, (long) (500 + Math.random() * 500));
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

}
