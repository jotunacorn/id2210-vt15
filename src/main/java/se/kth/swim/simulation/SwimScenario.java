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
package se.kth.swim.simulation;

import org.javatuples.Pair;
import se.kth.swim.AggregatorComp;
import se.kth.swim.HostComp;
import se.kth.swim.SwimComp;
import se.kth.swim.croupier.CroupierConfig;
import se.sics.p2ptoolbox.simulator.cmd.OperationCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.*;
import se.sics.p2ptoolbox.simulator.core.network.NetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.DeadLinkNetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.DisconnectedNodesNetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.UniformRandomModel;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.Distribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.BasicIntSequentialDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.IntegerUniformDistribution;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimScenario {

    private static final int NUMBER_OF_NODES = 10;
    private static final int BOOTSTRAP_SIZE = 2;
    private static final int KILL_SIZE = 5;
    private static final boolean ALLOW_NAT = true;

    private static long seed;
    private static InetAddress localHost;

    private static Set<Integer> killedNodes;

    private static CroupierConfig croupierConfig = new CroupierConfig(10, 5, 2000, 1000);

    static {
        try {
            localHost = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    //Make sure that your dead link set reflect the nodes in your system
    private static final Map<Integer, Set<Pair<Integer, Integer>>> deadLinksSets = new HashMap<Integer, Set<Pair<Integer, Integer>>>();

    static {
        Set<Pair<Integer, Integer>> deadLinks;

        deadLinks = new HashSet<Pair<Integer, Integer>>();
        deadLinks.add(Pair.with(10, 12));
        deadLinks.add(Pair.with(12, 10));
        deadLinksSets.put(1, deadLinks);

        deadLinks = new HashSet<Pair<Integer, Integer>>();
        deadLinks.add(Pair.with(10, 12));
        deadLinks.add(Pair.with(12, 10));
        deadLinks.add(Pair.with(13, 10));
        deadLinksSets.put(2, deadLinks);
    }

    static Operation1<StartAggregatorCmd, Integer> startAggregatorOp = new Operation1<StartAggregatorCmd, Integer>() {

        @Override
        public StartAggregatorCmd generate(final Integer nodeId) {
            return new StartAggregatorCmd<AggregatorComp, NatedAddress>() {
                private NatedAddress aggregatorAddress;

                @Override
                public Class getNodeComponentDefinition() {
                    return AggregatorComp.class;
                }

                @Override
                public AggregatorComp.AggregatorInit getNodeComponentInit() {
                    aggregatorAddress = new BasicNatedAddress(new BasicAddress(localHost, 23456, nodeId));
                    return new AggregatorComp.AggregatorInit(aggregatorAddress);
                }

                @Override
                public NatedAddress getAddress() {
                    return aggregatorAddress;
                }

            };
        }
    };

    static Operation1<StartNodeCmd, Integer> startNodeOp = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<HostComp, NatedAddress>() {
                private NatedAddress nodeAddress;

                @Override
                public Class getNodeComponentDefinition() {
                    return HostComp.class;
                }

                @Override
                public HostComp.HostInit getNodeComponentInit(NatedAddress aggregatorServer, Set<NatedAddress> bootstrapNodes) {
                    if(ALLOW_NAT) {
                        if (nodeId % 2 == 0) {
                            //open address
                            nodeAddress = new BasicNatedAddress(new BasicAddress(localHost, 12345, nodeId));
                        } else {
                            //nated address
                            nodeAddress = new BasicNatedAddress(new BasicAddress(localHost, 12345, nodeId), NatType.NAT, bootstrapNodes);
                        }
                    }
                    else{
                        nodeAddress = new BasicNatedAddress(new BasicAddress(localHost, 12345, nodeId));
                    }
                    /**
                     * we don't want all nodes to start their pseudo random
                     * generators with same seed else they might behave the same
                     */
                    long nodeSeed = seed + nodeId;
                    return new HostComp.HostInit(nodeAddress, bootstrapNodes, aggregatorServer, nodeSeed, croupierConfig);
                }

                @Override
                public Integer getNodeId() {
                    return nodeId;
                }

                @Override
                public NatedAddress getAddress() {
                    return nodeAddress;
                }

                @Override
                public int bootstrapSize() {
                    return BOOTSTRAP_SIZE;
                }

            };
        }
    };

    static Operation1<KillNodeCmd, Integer> killNodeOp = new Operation1<KillNodeCmd, Integer>() {

        public KillNodeCmd generate(final Integer nodeId) {
            SwimComp.log.info("Killing node {}", nodeId);
            return new KillNodeCmd() {
                public Integer getNodeId() {
                    killedNodes.add(nodeId);
                    return nodeId;
                }
            };
        }

    };

    //Usable NetworkModels:
    //1. UniformRandomModel
    //parameters: minimum link latency, maximum link latency
    //by default Simulator starts with UniformRandomModel(50, 500), so minimum link delay:50ms, maximum link delay:500ms
    //2. DeadLinkNetworkModel
    //composite network model that can be built on any other network model
    //parameters: network model, set of dead links (directed links)
    //Pair<1,2> means if node 1 will try to send a message to node 2, the simulator will drop this message, since this is a dead link
    //3. DisconnectedNodesNetworkModel
    //composite network model that can be built on any other network model
    //parameters: network model, set of disconnected nodes
    //a disconnected node will not be able to send or receive messages
    static Operation1<ChangeNetworkModelCmd, Integer> disconnectedNodesNMOp = new Operation1<ChangeNetworkModelCmd, Integer>() {

        @Override
        public ChangeNetworkModelCmd generate(Integer setIndex) {
            NetworkModel baseNetworkModel = new UniformRandomModel(50, 500);
            NetworkModel compositeNetworkModel = new DisconnectedNodesNetworkModel(setIndex, baseNetworkModel, null);//disconnectedNodesSets.get(setIndex));
            return new ChangeNetworkModelCmd(compositeNetworkModel);
        }
    };

    static Operation1<ChangeNetworkModelCmd, Integer> deadLinksNMOp = new Operation1<ChangeNetworkModelCmd, Integer>() {

        @Override
        public ChangeNetworkModelCmd generate(Integer setIndex) {
            NetworkModel baseNetworkModel = new UniformRandomModel(50, 500);
            NetworkModel compositeNetworkModel = new DeadLinkNetworkModel(setIndex, baseNetworkModel, deadLinksSets.get(setIndex));
            return new ChangeNetworkModelCmd(compositeNetworkModel);
        }
    };

    static Operation<SimulationResult> simulationResult = new Operation<SimulationResult>() {

        public SimulationResult generate() {
            return new SimulationResult() {

                @Override
                public void setSimulationResult(OperationCmd.ValidationException failureCause) {
                    SwimSimulationResult.failureCause = failureCause;
                }
            };
        }
    };

    //Operations require Distributions as parameters
    //1.ConstantDistribution - this will provide same parameter no matter how many times it is called
    //2.BasicIntSequentialDistribution - on each call it gives the next int. Works more or less like a counter
    //3.GenIntSequentialDistribution - give it a vector. It will draw elements from it on each call. 
    //Once out of elements it will give null. 
    //So be carefull for null pointer exception if you draw more times than elements
    //check se.sics.p2ptoolbox.simulator.dsl.distribution for more distributions
    //you can implement your own - by extending Distribution
    public static SimulationScenario simpleBoot(final long seed) {
        SwimScenario.seed = seed;

        killedNodes = new HashSet<>();

        SimulationScenario scen = new SimulationScenario() {
            {
                StochasticProcess startAggregator = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };

                StochasticProcess startPeers = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(0));
                        raise(NUMBER_OF_NODES, startNodeOp, new BasicIntSequentialDistribution(10));
                    }
                };

                StochasticProcess killPeers = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(0*1000));
                        raise(KILL_SIZE, killNodeOp, new RandomDistribution(getNodesToKill(KILL_SIZE)));
                    }
                };

                StochasticProcess deadLinks1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, deadLinksNMOp, new ConstantDistribution(Integer.class, 1));
                    }
                };

                StochasticProcess disconnectedNodes1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, disconnectedNodesNMOp, new ConstantDistribution(Integer.class, 1));
                    }
                };

                StochasticProcess fetchSimulationResult = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };

                startAggregator.start();
                startPeers.startAfterTerminationOf(1000, startAggregator);
                //killPeers.startAfterTerminationOf(50 * 1000, startPeers);
                // deadLinks1.startAfterTerminationOf(10000,startPeers);
                //  disconnectedNodes1.startAfterTerminationOf(10000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(100 * 1000, startPeers);
                terminateAfterTerminationOf(1000, fetchSimulationResult);

            }
        };

        scen.setSeed(seed);

        return scen;
    }


    private static Set<Integer> getNodesToKill(int count) {
        Set<Integer> nodesToKill = new HashSet<>();

        while (nodesToKill.size() < count && (nodesToKill.size() + killedNodes.size()) < NUMBER_OF_NODES) {
            int nodeNumber = (int) (Math.random() * NUMBER_OF_NODES + 10);

            if (!killedNodes.contains(nodeNumber)) {
                nodesToKill.add(nodeNumber);
            }
        }

        return nodesToKill;
    }

    static class RandomDistribution extends Distribution<Integer> {

        private LinkedList<Integer> nodesToKill;

        public RandomDistribution(Set<Integer> nodesToKill) {
            super(Type.OTHER, Integer.class);

            this.nodesToKill = new LinkedList<>(nodesToKill);
        }

        protected RandomDistribution(Type type, Class<Integer> numberType) {
            super(type, numberType);
        }

        @Override
        public Integer draw() {
            return nodesToKill.pollFirst();
        }
    }

}
