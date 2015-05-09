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
import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class AggregatorComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(AggregatorComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;

    public static Map<Integer, Map<Address, Status>> statuses;

    public AggregatorComp(AggregatorInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", new Object[]{selfAddress.getId()});

        statuses = new HashMap<>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress});
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress});
        }

    };

    private Handler<NetStatus> handleStatus = new Handler<NetStatus>() {

        @Override
        public void handle(NetStatus status) {
            //(log.info("{} status nr:{} from:{} received-pings:{} sent-pings:{}, Alive nodes: {}", new Object[]{selfAddress.getId(),status.getContent().statusNr, status.getHeader().getSource(), status.getContent().receivedPings, status.getContent().sentPings, status.getContent().getAliveNodes()});

            Map<Address, Status> statusesFromNode = statuses.get(status.getContent().getStatusNr());

            if (statusesFromNode == null) {
                statusesFromNode = new HashMap<>();
                statuses.put(status.getContent().getStatusNr(), statusesFromNode);
            }

            statusesFromNode.put(status.getSource().getBaseAdr(), status.getContent());
        }
    };

    public static void calculateConvergence() {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("convergance.txt", "UTF-8");
        }
        catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Map<Integer, Double> convergenceByStatusNr = new HashMap<>();

        for (int statusNr : statuses.keySet()) {
            Map<Address, Status> statusesForNr = statuses.get(statusNr);

            Set<Address> allAliveNodes = new HashSet<>();
            Set<Address> commonAliveNodes = null;

            for (Address address : statusesForNr.keySet()) {
                Status status = statusesForNr.get(address);

                NatedAddress natedAddress = new BasicNatedAddress((BasicAddress) address);
                status.getAliveNodes().put(natedAddress, 0);

                allAliveNodes.addAll(convertToAddress(status.getAliveNodes().keySet()));

                if (commonAliveNodes == null) {
                    commonAliveNodes = new HashSet<>(convertToAddress(status.getAliveNodes().keySet()));
                }
                else {
                    commonAliveNodes.retainAll(convertToAddress(status.getAliveNodes().keySet()));
                }
            }

            double convergenceRate = (double) commonAliveNodes.size() / (double) Math.max(1, allAliveNodes.size());

            if (convergenceRate > 1) {
                convergenceRate = 1 / convergenceRate;
            }

            log.info("alive nodes is " + commonAliveNodes.size() + " all alive is " + allAliveNodes.size());
            convergenceByStatusNr.put(statusNr, convergenceRate);
        }

        for (int statusNr : convergenceByStatusNr.keySet()) {
            SwimComp.log.info("Convergence at iteration {}: {}", statusNr, convergenceByStatusNr.get(statusNr));
            writer.println(convergenceByStatusNr.get(statusNr));
        }
        writer.close();
    }

    private static Set<Address> convertToAddress(Set<NatedAddress> nodes) {
        Set<Address> addresses = new HashSet<>();
        for (NatedAddress node : nodes) {
            addresses.add(node.getBaseAdr());
        }
        return addresses;
    }

    public static class AggregatorInit extends Init<AggregatorComp> {

        public final NatedAddress selfAddress;

        public AggregatorInit(NatedAddress selfAddress) {
            this.selfAddress = selfAddress;
        }
    }
}
