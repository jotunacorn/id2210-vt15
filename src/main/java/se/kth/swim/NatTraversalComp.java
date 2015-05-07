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

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.croupier.CroupierPort;
import se.kth.swim.croupier.msg.CroupierSample;
import se.kth.swim.croupier.util.Container;
import se.kth.swim.msg.net.NetMsg;
import se.kth.swim.msg.net.NetNatPing;
import se.kth.swim.msg.net.NetNatPong;
import se.kth.swim.timeouts.PingTimeout;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Header;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;
import se.sics.p2ptoolbox.util.network.impl.RelayHeader;
import se.sics.p2ptoolbox.util.network.impl.SourceHeader;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NatTraversalComp extends ComponentDefinition {

    private static final int HEARTBEAT_TIMEOUT = 1000;
    private static final int PING_TIMEOUT = 1000;
    private static final int PARENTS_COUNT = 2;

    private static final Logger log = LoggerFactory.getLogger(NatTraversalComp.class);
    private Negative<Network> local = provides(Network.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    private final Random rand;

    private Set<NatedAddress> samplePeers;
    private Set<NatedAddress> pingedParents;
    private Set<NatedAddress> deadParents; //Oh no

    public NatTraversalComp(NatTraversalInit init) {
        this.selfAddress = init.selfAddress;
//        log.info("{} {} initiating...", new Object[]{selfAddress.getId(), (selfAddress.isOpen() ? "OPEN" : "NATED")});

        this.rand = new Random(init.seed);

        this.samplePeers = new HashSet<>();
        this.pingedParents = new HashSet<>();
        this.deadParents = new HashSet<>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleIncomingMsg, network);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleOutgoingMsg, local);
        subscribe(handleCroupierSample, croupier);
        subscribe(handleHeartbeatTimeout, timer);
        subscribe(handlePingTimeout, timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
//            log.info("{} starting...", new Object[]{selfAddress.getId()});
            scheduleHeartbeating();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
//            log.info("{} stopping...", new Object[]{selfAddress.getId()});
        }

    };

    private Handler<NetMsg<Object>> handleIncomingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
//            log.trace("{} received msg:{}", new Object[]{selfAddress.getId(), msg});
            Header<NatedAddress> header = msg.getHeader();
            if (header instanceof SourceHeader) {
                if (!selfAddress.isOpen()) {
                    throw new RuntimeException("source header msg received on nated node - nat traversal logic error");
                }
                SourceHeader<NatedAddress> sourceHeader = (SourceHeader<NatedAddress>) header;
                if (sourceHeader.getActualDestination().getParents().contains(selfAddress)) {
//                    log.info("{} relaying message for:{}", new Object[]{selfAddress.getId(), sourceHeader.getSource()});
                    RelayHeader<NatedAddress> relayHeader = sourceHeader.getRelayHeader();
                    trigger(msg.copyMessage(relayHeader), network);
                    return;
                }
                else {
//                    log.warn("{} received weird relay message:{} - dropping it", new Object[]{selfAddress.getId(), msg});
                    return;
                }
            }
            else if (header instanceof RelayHeader) {
                if (selfAddress.isOpen()) {
                    throw new RuntimeException("relay header msg received on open node - nat traversal logic error");
                }
                RelayHeader<NatedAddress> relayHeader = (RelayHeader<NatedAddress>) header;
//                log.info("{} delivering relayed message:{} from:{}", new Object[]{selfAddress.getId(), msg, relayHeader.getActualSource()});
                Header<NatedAddress> originalHeader = relayHeader.getActualHeader();
                trigger(msg.copyMessage(originalHeader), local);
                return;
            }
            else {
//                log.info("{} delivering direct message:{} from:{}", new Object[]{selfAddress.getId(), msg, header.getSource()});
                trigger(msg, local);
                return;
            }
        }

    };

    private Handler<NetMsg<Object>> handleOutgoingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
//            log.trace("{} sending msg:{}", new Object[]{selfAddress.getId(), msg});
            Header<NatedAddress> header = msg.getHeader();
            if (header.getDestination().isOpen()) {
//                log.info("{} sending direct message:{} to:{}", new Object[]{selfAddress.getId(), msg, header.getDestination()});
                trigger(msg, network);
                return;
            }
            else {
                if (header.getDestination().getParents().isEmpty()) {
                    throw new RuntimeException("nated node with no parents");
                }
                NatedAddress parent = randomNode(header.getDestination().getParents());
                SourceHeader<NatedAddress> sourceHeader = new SourceHeader(header, parent);
//                log.info("{} sending message:{} to relay:{}", new Object[]{selfAddress.getId(), msg, parent});
                trigger(msg.copyMessage(sourceHeader), network);
                return;
            }
        }

    };

    private Handler handleCroupierSample = new Handler<CroupierSample>() {
        @Override
        public void handle(CroupierSample event) {
            log.info("{} croupier public nodes:{}", selfAddress.getBaseAdr(), event.publicSample);
            if (!selfAddress.isOpen()) {
                //use this to change parent in case it died
                Set<Container<NatedAddress, Object>> publicSample = new HashSet<>(event.publicSample);
                for (Container<NatedAddress, Object> container : publicSample) {
                    samplePeers.add(container.getSource());
                }
                samplePeers.removeAll(deadParents);

                List<NatedAddress> samplePeerList = new ArrayList<>(samplePeers);
                Collections.shuffle(samplePeerList);
                for (NatedAddress address : samplePeerList) {
                    if (selfAddress.getParents().size() >= PARENTS_COUNT) {
                        break;
                    }

                    if (!address.equals(selfAddress)) {
                        selfAddress.getParents().add(address);
                    }
                }
            }
        }
    };

    private Handler<NetNatPing> handlePing = new Handler<NetNatPing>() {
        @Override
        public void handle(NetNatPing netNatPing) {
            trigger(new NetNatPong(selfAddress, netNatPing.getSource()), network);
        }
    };

    private Handler<NetNatPong> handlePong = new Handler<NetNatPong>() {
        @Override
        public void handle(NetNatPong netNatPong) {
            pingedParents.remove(netNatPong.getSource());
        }
    };

    private Handler<HeartbeatTimeout> handleHeartbeatTimeout = new Handler<HeartbeatTimeout>() {
        @Override
        public void handle(HeartbeatTimeout heartbeatTimeout) {
            for (NatedAddress address : selfAddress.getParents()) {
                trigger(new NetNatPing(selfAddress, address), network);

                pingedParents.add(address);

                ScheduleTimeout spt = new ScheduleTimeout(PING_TIMEOUT);
                NatPingTimeout sc = new NatPingTimeout(spt, address);
                spt.setTimeoutEvent(sc);
                trigger(spt, timer);
            }
        }
    };

    private Handler<NatPingTimeout> handlePingTimeout = new Handler<NatPingTimeout>() {
        @Override
        public void handle(NatPingTimeout natPingTimeout) {
            if (pingedParents.contains(natPingTimeout.getAddress())) {
                deadParents.add(natPingTimeout.getAddress());
                pingedParents.remove(natPingTimeout.getAddress());

                selfAddress.getParents().remove(natPingTimeout.getAddress());
            }
        }
    };

    private void scheduleHeartbeating() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(HEARTBEAT_TIMEOUT, HEARTBEAT_TIMEOUT);
        HeartbeatTimeout sc = new HeartbeatTimeout(spt);
        spt.setTimeoutEvent(sc);
        trigger(spt, timer);
    }

    private NatedAddress randomNode(Set<NatedAddress> nodes) {
        int index = rand.nextInt(nodes.size());
        Iterator<NatedAddress> it = nodes.iterator();
        while (index > 0) {
            it.next();
            index--;
        }
        return it.next();
    }

    public static class NatTraversalInit extends Init<NatTraversalComp> {

        public final NatedAddress selfAddress;
        public final long seed;

        public NatTraversalInit(NatedAddress selfAddress, long seed) {
            this.selfAddress = selfAddress;
            this.seed = seed;
        }
    }

    class HeartbeatTimeout extends Timeout {

        protected HeartbeatTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    class NatPingTimeout extends Timeout {

        private NatedAddress address;

        protected NatPingTimeout(ScheduleTimeout request, NatedAddress address) {
            super(request);
            this.address = address;
        }

        public NatedAddress getAddress() {
            return address;
        }
    }

}
