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
package se.kth.swim.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.parentport.NewParentAlert;
import se.kth.swim.msg.parentport.ParentPort;
import se.kth.swim.component.init.NatTraversalInit;
import se.kth.swim.croupier.CroupierPort;
import se.kth.swim.croupier.msg.CroupierSample;
import se.kth.swim.croupier.util.Container;
import se.kth.swim.msg.net.NetMsg;
import se.kth.swim.msg.net.NetNatPing;
import se.kth.swim.msg.net.NetNatPong;
import se.kth.swim.simulation.SwimScenario;
import se.kth.swim.timeout.HeartbeatTimeout;
import se.kth.swim.timeout.NatPingTimeout;
import se.sics.kompics.*;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Header;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.RelayHeader;
import se.sics.p2ptoolbox.util.network.impl.SourceHeader;

import java.util.*;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NatTraversalComp extends ComponentDefinition {

    private static final boolean ENABLE_PROVIDED_LOGGING = false;
    private static final boolean ENABLE_OUR_LOGGING = false;
    private static final int HEARTBEAT_TIMEOUT = 500;   //Timeout between heartbeats
    private static final int PING_TIMEOUT = 500;        //Timeout to receive a pong

    private static final Logger log = LoggerFactory.getLogger(NatTraversalComp.class);
    private Negative<Network> local = provides(Network.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Negative<ParentPort> parentPort = provides(ParentPort.class);

    private final NatedAddress selfAddress;
    private final Random rand;

    private int sentPings;                              //Number of times we have hearbeated
    private Set<Integer> pingedParents;                 //Set of parents we've pinged but not received a pong from
    private Set<NatedAddress> latestParentSample;       //Latest sample received from croupier
    private Set<Address> deadParents;                   //Oh no! Set of parents declared dead

    public NatTraversalComp(NatTraversalInit init) {
        this.selfAddress = init.selfAddress;

        if (ENABLE_PROVIDED_LOGGING) {
            log.info("{} {} initiating...", new Object[]{selfAddress.getId(), (selfAddress.isOpen() ? "OPEN" : "NATED")});
        }

        this.rand = new Random(init.seed);

        this.pingedParents = new HashSet<>();
        this.deadParents = new HashSet<>();
        this.latestParentSample = new HashSet<>();
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
            if (ENABLE_PROVIDED_LOGGING) {
                log.info("{} starting...", new Object[]{selfAddress.getId()});
            }
            scheduleHeartbeating();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if (ENABLE_PROVIDED_LOGGING) {
                log.info("{} stopping...", new Object[]{selfAddress.getId()});
            }
        }

    };

    private Handler<NetMsg<Object>> handleIncomingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
            if (ENABLE_PROVIDED_LOGGING) {
                log.trace("{} received msg:{}", new Object[]{selfAddress.getId(), msg});
            }
            Header<NatedAddress> header = msg.getHeader();
            if (header instanceof SourceHeader) {
                if (!selfAddress.isOpen()) {
                    throw new RuntimeException("source header msg received on nated node - nat traversal logic error");
                }
                SourceHeader<NatedAddress> sourceHeader = (SourceHeader<NatedAddress>) header;
                if (sourceHeader.getActualDestination().getParents().contains(selfAddress)) {
                    if (ENABLE_PROVIDED_LOGGING) {
                        log.info("{} relaying message for:{}", new Object[]{selfAddress.getId(), sourceHeader.getSource()});
                    }
                    RelayHeader<NatedAddress> relayHeader = sourceHeader.getRelayHeader();
                    trigger(msg.copyMessage(relayHeader), network);
                    return;
                }
                else {
                    if (ENABLE_PROVIDED_LOGGING) {
                        log.warn("{} received weird relay message:{} - dropping it", new Object[]{selfAddress.getId(), msg});
                    }
                    return;
                }
            }
            else if (header instanceof RelayHeader) {
                if (selfAddress.isOpen()) {
                    throw new RuntimeException("relay header msg received on open node - nat traversal logic error");
                }
                RelayHeader<NatedAddress> relayHeader = (RelayHeader<NatedAddress>) header;
                if (ENABLE_PROVIDED_LOGGING) {
                    log.info("{} delivering relayed message:{} from:{}", new Object[]{selfAddress.getId(), msg, relayHeader.getActualSource()});
                }
                Header<NatedAddress> originalHeader = relayHeader.getActualHeader();
                trigger(msg.copyMessage(originalHeader), local);
                return;
            }
            else {
                if (ENABLE_PROVIDED_LOGGING) {
                    log.info("{} delivering direct message:{} from:{}", new Object[]{selfAddress.getId(), msg, header.getSource()});
                }
                trigger(msg, local);
                return;
            }
        }

    };

    private Handler<NetMsg<Object>> handleOutgoingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
            if (ENABLE_PROVIDED_LOGGING) {
                log.trace("{} sending msg:{}", new Object[]{selfAddress.getId(), msg});
            }
            Header<NatedAddress> header = msg.getHeader();
            if (header.getDestination().isOpen()) {
                if (ENABLE_PROVIDED_LOGGING) {
                    log.info("{} sending direct message:{} to:{}", new Object[]{selfAddress.getId(), msg, header.getDestination()});
                }
                trigger(msg, network);
                return;
            }
            else {
                if (header.getDestination().getParents().isEmpty()) {
                    throw new RuntimeException("nated node with no parents in node " + selfAddress + ". The orphan is " + header.getDestination());
                }
                NatedAddress parent = randomNode(header.getDestination().getParents());
                SourceHeader<NatedAddress> sourceHeader = new SourceHeader(header, parent);
                if (ENABLE_PROVIDED_LOGGING) {
                    log.info("{} sending message:{} to relay:{}", new Object[]{selfAddress.getId(), msg, parent});
                }
                trigger(msg.copyMessage(sourceHeader), network);
                return;
            }
        }

    };

    //Handler to receive new sample from croupier in
    private Handler handleCroupierSample = new Handler<CroupierSample>() {
        @Override
        public void handle(CroupierSample event) {
            latestParentSample.clear();

            if (ENABLE_OUR_LOGGING) {
                log.info("{} croupier public nodes:{}", selfAddress.getBaseAdr(), event.publicSample);
            }

            if (!selfAddress.isOpen()) {
                //use this to change parent in case it died
                Set<Container<NatedAddress, Object>> publicSample = new HashSet<>(event.publicSample);
                for (Container<NatedAddress, Object> container : publicSample) {
                    latestParentSample.add(container.getSource()); //Add all received sources to a set
                }
                if (ENABLE_OUR_LOGGING) {
                    if (latestParentSample.size() == 0) {
                        log.info(selfAddress + "RECEIVED EMPTY SAMPLE");
                    }
                }
                sendNewParents(latestParentSample);                 //Update our parents if someone has died
            }
        }
    };

    /**
     * Takes a set of peers. Filters out those marked as dead and will send
     * new parents to the SWIM layer if needed.
     * Needed only if current parent count is less than PARENTS_COUNT constant.
     */
    private void sendNewParents(Set<NatedAddress> inputPeers) {
        Set<NatedAddress> samplePeers = new HashSet<>();
        for (NatedAddress node : inputPeers) { //Filter out the dead parents
            if (!deadParents.contains(node.getBaseAdr())) {
                samplePeers.add(node);
            }
        }
        for (NatedAddress node : selfAddress.getParents()) { //Filter out the dead parents
            if (!deadParents.contains(node.getBaseAdr())) {
                samplePeers.add(node);
            }
        }

        List<NatedAddress> samplePeerList = new ArrayList<>(samplePeers); //Create a list to retrieve peers from
        Collections.shuffle(samplePeerList, rand);
        Set<NatedAddress> aliveParents = new HashSet<NatedAddress>(selfAddress.getParents());
        Set<NatedAddress> addressesToRemove = new HashSet<>();
        for (NatedAddress node : aliveParents) {
            if (deadParents.contains(node.getBaseAdr()))
                addressesToRemove.add(node);
        }
        aliveParents.removeAll(addressesToRemove);
        if (ENABLE_PROVIDED_LOGGING) {
            if (aliveParents.size() < SwimScenario.bootstrapSize) {
                log.info(selfAddress + "I'm low on parents. THe croupier sample is " + latestParentSample);
            }
        }
        boolean listUpdated = false;                                      //Boolean which gets set to true if a parent has been replaced
        for (NatedAddress address : samplePeerList) {                     //Loop the list and break if we have enough parents
            if (aliveParents.size() >= SwimScenario.bootstrapSize) {
                break;
            }

            if (!address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
                listUpdated = true;
                aliveParents.add(address);
            }
        }

        if (listUpdated) {                                              //Send the parents to SwimComp if we have new parents
            Set<NatedAddress> setToSend = new HashSet<>(aliveParents);

            if (ENABLE_OUR_LOGGING) {
                log.info("Sending a new parent! The list is " + setToSend);
            }

            trigger(new NewParentAlert(setToSend), parentPort);
        }
    }

    //Handler to answer to heartbeats in.
    private Handler<NetNatPing> handlePing = new Handler<NetNatPing>() {
        @Override
        public void handle(NetNatPing netNatPing) {
            if (ENABLE_OUR_LOGGING) {
                log.info("Answering hearbeat from " + netNatPing.getSource() + ". I'm node " + selfAddress);
            }

            trigger(new NetNatPong(selfAddress, netNatPing.getSource(), netNatPing.getContent().getPingNr()), network);
        }
    };

    //Handler to receive Pongs in. If we receive a pong we remove the node from pingedParents
    private Handler<NetNatPong> handlePong = new Handler<NetNatPong>() {
        @Override
        public void handle(NetNatPong netNatPong) {
            if (ENABLE_OUR_LOGGING) {
                log.info("Received a NatPong from " + netNatPong.getSource() + ". I'm node " + selfAddress);
            }

            pingedParents.remove(netNatPong.getContent().getPingNr());
        }
    };

    //Handler to send pings from. We loop through all parents and ping them.
    private Handler<HeartbeatTimeout> handleHeartbeatTimeout = new Handler<HeartbeatTimeout>() {
        @Override
        public void handle(HeartbeatTimeout heartbeatTimeout) {

            for (NatedAddress address : selfAddress.getParents()) {
                if (ENABLE_OUR_LOGGING) {
                    log.info("Sending a hearbeat from address " + selfAddress + " to address " + address);
                }

                trigger(new NetNatPing(selfAddress, address, sentPings), network);

                pingedParents.add(sentPings);

                ScheduleTimeout spt = new ScheduleTimeout(PING_TIMEOUT);
                NatPingTimeout sc = new NatPingTimeout(spt, address, sentPings);
                sentPings++;
                spt.setTimeoutEvent(sc);
                trigger(spt, timer);
            }
        }
    };

    //handler to handle timed out pings in. If a node still is in the pingedParents Set it's declared dead.
    private Handler<NatPingTimeout> handlePingTimeout = new Handler<NatPingTimeout>() {
        @Override
        public void handle(NatPingTimeout natPingTimeout) {
            if (pingedParents.contains(natPingTimeout.getPingNr())) {
                if (ENABLE_OUR_LOGGING) {
                    log.info("Declaring node " + natPingTimeout.getAddress() + " dead. I'm node " + selfAddress);
                }

                deadParents.add(natPingTimeout.getAddress().getBaseAdr());
                pingedParents.remove(natPingTimeout.getPingNr());
                sendNewParents(latestParentSample);
            }
        }
    };

    //Will start scheduling pings
    private void scheduleHeartbeating() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(HEARTBEAT_TIMEOUT, HEARTBEAT_TIMEOUT);
        HeartbeatTimeout sc = new HeartbeatTimeout(spt);
        spt.setTimeoutEvent(sc);
        trigger(spt, timer);
    }

    //Returns a random node from the set. Method provided.
    private NatedAddress randomNode(Set<NatedAddress> nodes) {
        int index = rand.nextInt(nodes.size());
        Iterator<NatedAddress> it = nodes.iterator();
        while (index > 0) {
            it.next();
            index--;
        }
        return it.next();
    }

}
