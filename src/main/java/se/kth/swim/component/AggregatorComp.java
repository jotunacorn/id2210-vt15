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
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.*;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class AggregatorComp extends ComponentDefinition {

    private static final boolean ENABLE_LOGGING = false;

    private static final Logger log = LoggerFactory.getLogger(AggregatorComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;

    //Nested hashmaps to hold status reports. Key to the outer is the status number. Key to inner is address who sent the report.
    public static Map<Integer, Map<Address, Status>> statuses;

    public AggregatorComp(AggregatorInit init) {
        this.selfAddress = init.selfAddress;

        if (ENABLE_LOGGING) {
            log.info("{} initiating...", new Object[]{selfAddress.getId()});
        }

        statuses = new HashMap<>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (ENABLE_LOGGING) {
                log.info("{} starting...", new Object[]{selfAddress});
            }
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if (ENABLE_LOGGING) {
                log.info("{} stopping...", new Object[]{selfAddress});
            }
        }

    };

    /**
     * Handler for status reports. Statuses are sent periodically by the SwimComponent.
     */
    private Handler<NetStatus> handleStatus = new Handler<NetStatus>() {

        @Override
        public void handle(NetStatus status) {
            if (ENABLE_LOGGING) {
                log.info("{} status nr:{} from:{} received-pings:{} sent-pings:{}, Alive nodes: {}", new Object[]{selfAddress.getId(), status.getContent().statusNr, status.getHeader().getSource(), status.getContent().receivedPings, status.getContent().sentPings, status.getContent().getAliveNodes()});
            }

            //Put the status in the appropriate HashMap for later.
            Map<Address, Status> statusesFromNode = statuses.get(status.getContent().getStatusNr());

            if (statusesFromNode == null) {
                statusesFromNode = new HashMap<>();
                statuses.put(status.getContent().getStatusNr(), statusesFromNode);
            }

            statusesFromNode.put(status.getSource().getBaseAdr(), status.getContent());
        }
    };

    /**
     * Performs a convergence calculation based on the previously reported statuses.
     * This method is called after the simulation ended.
     */
    public static void calculateConvergence() {
        //Used to print raw values to a file for easier export.
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("convergance.txt", "UTF-8");
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Map<Integer, Double> convergenceByStatusNr = new HashMap<>();

        //Loop through all status numbers (rounds)
        for (int statusNr : statuses.keySet()) {
            Map<Address, Status> statusesForNr = statuses.get(statusNr);

            Set<Address> allAliveNodes = new HashSet<>();
            Set<Address> commonAliveNodes = null;

            int nrOfDisconnectedNodes = 0;
            //For every status, in the round...
            for (Address address : statusesForNr.keySet()) {
                Status status = statusesForNr.get(address);


                //Add the sender node to their own alive nodes list. This is important for the convergence calculation.
                NatedAddress natedAddress = new BasicNatedAddress((BasicAddress) address);

                status.getAliveNodes().put(natedAddress, 0);

                //Add all alive nodes to a set
                allAliveNodes.addAll(convertToAddress(status.getAliveNodes().keySet()));

                if (status.getAliveNodes().isEmpty()) {
                    nrOfDisconnectedNodes++;
                    System.out.println("Node nr " + ((BasicAddress) address).getId() + " doesn't have any alive nodes!");
                }
                else {
                    //Get the common alive nodes from all nodes.
                    if (commonAliveNodes == null) {
                        commonAliveNodes = new HashSet<>(convertToAddress(status.getAliveNodes().keySet()));
                    }
                    else {
                        commonAliveNodes.retainAll(convertToAddress(status.getAliveNodes().keySet()));
                    }
                }

            }

            //The convergence is calculated as common nodes divided by total nodes in the system.
            //If all nodes have all other (alive) nodes in their alive lists, the convergence rate will be 1.
            double convergenceRate = Math.max(((double) (commonAliveNodes.size() - nrOfDisconnectedNodes) / (double) Math.max(1, allAliveNodes.size())), 0);

            if (convergenceRate > 1) { //Invert it if it's higher than 1. A value higher than 1 means that the number of nodes stored in the alive lists is higher than the actual number of alive nodes.
                convergenceRate = 1 / convergenceRate;
            }

            log.info("Number of alive nodes: " + commonAliveNodes.size() + ", Number of alive nodes: " + allAliveNodes.size());
            convergenceByStatusNr.put(statusNr, convergenceRate);
        }

        for (int statusNr : convergenceByStatusNr.keySet()) {
            SwimComp.log.info("Convergence at iteration {}: {}", statusNr, convergenceByStatusNr.get(statusNr));
            writer.println(convergenceByStatusNr.get(statusNr).toString().replace(".", ","));
        }
        writer.close();
    }

    /**
     * Converts a set of NatedAddresses to a set of Addresses.
     * This is needed because the hashcode of the NatedAddress changes if the parents change.
     */
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
