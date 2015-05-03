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
import se.kth.swim.msg.net.*;
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

    private static final int PING_TIMEOUT = 2000; //Time until a node is suspected
    private static final int SUSPECTED_TIMEOUT = 4000; //Time until it's declared dead
    private static final int AGGREGATOR_TIMEOUT = 1000; //Delay between sending info to aggregator
    private static final boolean ENABLE_LOGGING = true;
    private static final int K = 1;

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
    private int sentStatuses = 0;

    private NodeHandler nodeHandler;

    private List<Integer> sentPingNrs;

    private Map<Integer, NatedAddress> sentIndirectPings;

    public SwimComp(SwimInit init) {
        if(ENABLE_LOGGING)
            log.info("{} initiating...", init.selfAddress);

        selfAddress = init.selfAddress;
        aggregatorAddress = init.aggregatorAddress;

        nodeHandler = new NodeHandler(selfAddress);
        sentPingNrs = new ArrayList<>();
        sentIndirectPings = new HashMap<>();

        for (NatedAddress address : init.bootstrapNodes) {
            nodeHandler.addAlive(address, 0);
        }
        nodeHandler.printAliveNodes();
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleAlive, network);
        subscribe(handleNetKPing, network);
        subscribe(handleNetKPong, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handlePongTimeout, timer);
        subscribe(handleSuspectedTimeout, timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if(ENABLE_LOGGING)
            log.info("{} starting...", new Object[]{selfAddress.getId()});

            schedulePeriodicPing();
            schedulePeriodicStatus();
        }

    };

    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if(ENABLE_LOGGING)
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
            if(ENABLE_LOGGING)
            log.info("{} received pong nr {} from:{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getHeader().getSource()});

            boolean wasRegularPing = sentPingNrs.remove(Integer.valueOf(event.getContent().getPingNr()));

            if (wasRegularPing) {
                for (NatedAddress address : event.getContent().getNewNodes().keySet()) {
                    nodeHandler.addAlive(address, event.getContent().getNewNodes().get(address));
                }

                for (NatedAddress address : event.getContent().getSuspectedNodes().keySet()) {
                    nodeHandler.addSuspected(address, event.getContent().getSuspectedNodes().get(address));
                }

                for (NatedAddress address : event.getContent().getDeadNodes().keySet()) {
                    if(ENABLE_LOGGING)
                    log.info("{} Declared node {} dead from pong", new Object[]{selfAddress.getId(), address});
                    nodeHandler.addDead(address, event.getContent().getDeadNodes().get(address));
                }

                nodeHandler.addDefinatelyAlive(event.getSource(), event.getContent().getIncarnationCounter());
                //log.info("{} Restored suspected node: {}", new Object[]{selfAddress.getId(), event.getSource()});

                if (event.getContent().getSuspectedNodes().containsKey(selfAddress)) {
                    if(ENABLE_LOGGING)
                    log.info("{} Found self in suspected list from node: {}", new Object[]{selfAddress.getId(), event.getSource()});

                    incarnationCounter++;

                    for (NatedAddress address : nodeHandler.getAliveNodes().keySet()) {
                        trigger(new NetAlive(selfAddress, address, incarnationCounter), network);
                    }
                }
            }
            else if (sentIndirectPings.containsKey(event.getContent().getPingNr())) {
                if(ENABLE_LOGGING)
                log.info("{} forwarding KPing result for suspected node {} to: {}", new Object[]{selfAddress.getId(), event.getSource(), sentIndirectPings.get(event.getContent().getPingNr())});
                trigger(new NetKPong(selfAddress, sentIndirectPings.get(event.getContent().getPingNr()), event.getSource(), event.getContent().getIncarnationCounter()), network);
                sentIndirectPings.remove(event.getContent().getPingNr());
            }

            nodeHandler.printAliveNodes();
        }

    };

    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
            if(ENABLE_LOGGING)
            log.info("{} received ping nr {} from:{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getHeader().getSource()});

            receivedPings++;

            nodeHandler.addDefinatelyAlive(event.getSource(), event.getContent().getIncarnationCounter());
            if(ENABLE_LOGGING)
            log.info("{} sending pong nr {} to :{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getSource()});
            Pong pong = nodeHandler.getPong(event.getContent().getPingNr(), incarnationCounter);
            trigger(new NetPong(selfAddress, event.getSource(), pong), network);

            nodeHandler.printAliveNodes();
        }

    };

    private Handler<NetAlive> handleAlive = new Handler<NetAlive>() {

        @Override
        public void handle(NetAlive netAlive) {
            if(ENABLE_LOGGING)
            log.info("{} Restored suspected node by alive message from: {}", new Object[]{selfAddress.getId(), netAlive.getSource()});

            nodeHandler.addAlive(netAlive.getSource(), netAlive.getContent().getIncarnationCounter());
        }
    };

    private Handler<NetKPing> handleNetKPing = new Handler<NetKPing>() {

        @Override
        public void handle(NetKPing netKPing) {
            if(ENABLE_LOGGING)
            log.info("{} received KPing request for suspected node {}", new Object[]{selfAddress.getId(), netKPing.getContent().getAddressToPing()});

            trigger(new NetPing(selfAddress, netKPing.getContent().getAddressToPing(), sentPings, incarnationCounter), network);
            sentIndirectPings.put(sentPings, netKPing.getSource());
            sentPings++;
        }

    };

    private Handler<NetKPong> handleNetKPong = new Handler<NetKPong>() {

        @Override
        public void handle(NetKPong netKPong) {
            if(ENABLE_LOGGING)
            log.info("{} received KPong for suspected node {}", new Object[]{selfAddress.getId(), netKPong.getContent().getAddress()});
            nodeHandler.addDefinatelyAlive(netKPong.getContent().getAddress(), netKPong.getContent().getIncarnationCounter());
            nodeHandler.printAliveNodes();
        }

    };

    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
            NatedAddress partnerAddress = nodeHandler.getRandomAliveNode();

            if (partnerAddress != null) {
                if(ENABLE_LOGGING)
                log.info("{} sending ping nr {} to partner:{}", new Object[]{selfAddress.getId(), sentPings, partnerAddress});

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
            if(ENABLE_LOGGING)
            log.info("{} sending status nr:{} to aggregator:{}", new Object[]{selfAddress.getId(), sentStatuses, aggregatorAddress});
            Map<NatedAddress, Integer> sendAliveNodes = new HashMap<>(nodeHandler.getAliveNodes());
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(sentStatuses, receivedPings, sentPings, sendAliveNodes, nodeHandler.getSuspectedNodes(), nodeHandler.getDeadNodes())), network);

            sentStatuses++;
        }

    };

    private Handler<PongTimeout> handlePongTimeout = new Handler<PongTimeout>() {

        @Override
        public void handle(PongTimeout pongTimeout) {
            if (sentPingNrs.contains(pongTimeout.getPingNr())) {
                if(ENABLE_LOGGING)
                log.info("{} Suspected missing ping nr {} from node: {}", new Object[]{selfAddress.getId(), pongTimeout.getPingNr(), pongTimeout.getAddress()});

                nodeHandler.addSuspected(pongTimeout.getAddress());

                List<NatedAddress> aliveNodes = new ArrayList<>(nodeHandler.getAliveNodes().keySet());
                aliveNodes.remove(pongTimeout.getAddress());
                Collections.shuffle(aliveNodes);

                for (int i = 0; i < K && i < aliveNodes.size(); i++) {
                    if(ENABLE_LOGGING)
                    log.info("{} sending KPing for suspected node {} to: {}", new Object[]{selfAddress.getId(), pongTimeout.getAddress(), aliveNodes.get(i)});
                    trigger(new NetKPing(selfAddress, aliveNodes.get(i), pongTimeout.getAddress()), network);
                }

                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(SUSPECTED_TIMEOUT);
                SuspectedTimeout suspectedTimeout = new SuspectedTimeout(scheduleTimeout, pongTimeout.getAddress());
                scheduleTimeout.setTimeoutEvent(suspectedTimeout);
                trigger(scheduleTimeout, timer);

                //nodeHandler.printAliveNodes();
            }
        }
    };

    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>() {

        @Override
        public void handle(SuspectedTimeout suspectedTimeout) {
            //log.info("{} Suspected node timeout: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});

            if (nodeHandler.addDead(suspectedTimeout.getAddress())) {
                if(ENABLE_LOGGING)
                log.info("{} Declared node dead: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});

                //nodeHandler.printAliveNodes();
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
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, AGGREGATOR_TIMEOUT);
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
