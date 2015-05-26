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
import se.kth.swim.component.AggregatorComp;
import se.kth.swim.component.HostComp;
import se.kth.swim.component.SwimComp;
import se.kth.swim.croupier.CroupierConfig;
import se.sics.p2ptoolbox.simulator.cmd.OperationCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.*;
import se.sics.p2ptoolbox.simulator.core.network.NetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.DeadLinkNetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.UniformRandomModel;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.Distribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.BasicIntSequentialDistribution;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimScenario {

    private static long seed;

    private static Random rand;

    private static int simulationLength;

    private static int nodeCount;
    public static int bootstrapSize;
    private static boolean allowNat;
    private static int natedNodeFraction;

    private static int killSize;
    private static int killInterval;
    private static int failureAfter;

    private static InetAddress localHost;

    private static CroupierConfig croupierConfig = new CroupierConfig(10, 5, 1000, 500);

    static {
        try {
            localHost = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
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
                    if (allowNat) {
                        if ((nodeId % natedNodeFraction) == 1) {
                            //nated address
                            nodeAddress = new BasicNatedAddress(new BasicAddress(localHost, 12345, nodeId), NatType.NAT, bootstrapNodes);
                        }
                        else {
                            //open address
                            nodeAddress = new BasicNatedAddress(new BasicAddress(localHost, 12345, nodeId));
                        }
                    }
                    else {
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
                    return bootstrapSize;
                }

            };
        }
    };

    static Operation1<KillNodeCmd, Integer> killNodeOp = new Operation1<KillNodeCmd, Integer>() {

        public KillNodeCmd generate(final Integer nodeId) {

            SwimComp.log.info("Killing node {}", nodeId);

            return new KillNodeCmd() {
                public Integer getNodeId() {
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

    static Operation1<ChangeNetworkModelCmd, Integer> deadLinksNMOp = new Operation1<ChangeNetworkModelCmd, Integer>() {

        @Override
        public ChangeNetworkModelCmd generate(Integer setIndex) {
            Set<Pair<Integer, Integer>> linksToKill = getLinksToKill(killSize);

            SwimComp.log.info("Killing links: {}", linksToKill);

            NetworkModel baseNetworkModel = new UniformRandomModel(0, 0);
            NetworkModel compositeNetworkModel = new DeadLinkNetworkModel(setIndex, baseNetworkModel, linksToKill);
            return new ChangeNetworkModelCmd(compositeNetworkModel);
        }
    };

    static Operation<SimulationResult> simulationResult = new Operation<SimulationResult>() {

        public SimulationResult generate() {

            AggregatorComp.calculateConvergence();

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
    public static SimulationScenario simpleBoot(final long seed,
                                                final int simulationLength,
                                                final int nodeCount,
                                                final int bootstrapSize,
                                                final boolean allowNat,
                                                final int natedNodeFraction) { //TODO: Clean the regular scenario
        SwimScenario.seed = seed;
        SwimScenario.rand = new Random(seed);
        SwimScenario.simulationLength = simulationLength;
        SwimScenario.nodeCount = nodeCount;
        SwimScenario.bootstrapSize = bootstrapSize;
        SwimScenario.allowNat = allowNat;
        SwimScenario.natedNodeFraction = natedNodeFraction;

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
                        raise(nodeCount, startNodeOp, new BasicIntSequentialDistribution(10));
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
                fetchSimulationResult.startAfterTerminationOf(simulationLength * 1000, startPeers);
                terminateAfterTerminationOf(1000, fetchSimulationResult);
            }
        };

        scen.setSeed(seed);

        return scen;
    }


    public static SimulationScenario withNodeDeaths(final long seed,
                                                    final int simulationLength,
                                                    final int nodeCount,
                                                    final int bootstrapSize,
                                                    final boolean allowNat,
                                                    final int natedNodeFraction,
                                                    final int killSize,
                                                    final int killInterval,
                                                    final int failureAfter) {
        SwimScenario.seed = seed;
        SwimScenario.rand = new Random(seed);
        SwimScenario.simulationLength = simulationLength;
        SwimScenario.nodeCount = nodeCount;
        SwimScenario.bootstrapSize = bootstrapSize;
        SwimScenario.allowNat = allowNat;
        SwimScenario.natedNodeFraction = natedNodeFraction;
        SwimScenario.killSize = killSize;
        SwimScenario.killInterval = killInterval;
        SwimScenario.failureAfter = failureAfter;

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
                        raise(nodeCount, startNodeOp, new BasicIntSequentialDistribution(10));
                    }
                };

                StochasticProcess killPeers = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(killInterval * 1000));
                        raise(killSize, killNodeOp, new RandomDistribution(getNodesToKill(killSize)));
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
                killPeers.startAfterTerminationOf(failureAfter * 1000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(simulationLength * 1000, startPeers);
                terminateAfterTerminationOf(1000, fetchSimulationResult);
            }
        };

        scen.setSeed(seed);

        return scen;
    }


    public static SimulationScenario withLinkDeaths(final long seed,
                                                    final int simulationLength,
                                                    final int nodeCount,
                                                    final int bootstrapSize,
                                                    final boolean allowNat,
                                                    final int natedNodeFraction,
                                                    final int killSize,
                                                    final int killInterval,
                                                    final int failureAfter) {
        SwimScenario.seed = seed;
        SwimScenario.rand = new Random(seed);
        SwimScenario.simulationLength = simulationLength;
        SwimScenario.nodeCount = nodeCount;
        SwimScenario.bootstrapSize = bootstrapSize;
        SwimScenario.allowNat = allowNat;
        SwimScenario.natedNodeFraction = natedNodeFraction;
        SwimScenario.killSize = killSize;
        SwimScenario.killInterval = killInterval;
        SwimScenario.failureAfter = failureAfter;

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
                        raise(nodeCount, startNodeOp, new BasicIntSequentialDistribution(10));
                    }
                };

                StochasticProcess deadLinks = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, deadLinksNMOp, new ConstantDistribution(Integer.class, 1));
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
                deadLinks.startAfterTerminationOf(failureAfter * 1000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(simulationLength * 1000, startPeers);
                terminateAfterTerminationOf(1000, fetchSimulationResult);
            }
        };

        scen.setSeed(seed);

        return scen;
    }

    /**
     * Returns a set of specified number of random nodes.
     */
    private static Set<Integer> getNodesToKill(int count) {
        Set<Integer> nodesToKill = new HashSet<>();

        while (nodesToKill.size() < count && (nodesToKill.size()) < nodeCount) {
            int nodeNumber = (int) (rand.nextDouble() * nodeCount + 10);

            nodesToKill.add(nodeNumber);
        }

        return nodesToKill;
    }


    private static Set<Pair<Integer, Integer>> getLinksToKill(int count) {
        Set<Pair<Integer, Integer>> linksToKill = new HashSet<>();

        while (linksToKill.size() < count && (linksToKill.size()) < nodeCount) {
            int firstNodeNumber = (int) (rand.nextDouble() * nodeCount + 10);
            int secondNodeNumber = (int) (rand.nextDouble() * nodeCount + 10);

            if (firstNodeNumber != secondNodeNumber) {
                Pair<Integer, Integer> link = new Pair<>(firstNodeNumber, secondNodeNumber);

                linksToKill.add(link);
            }
        }

        return linksToKill;
    }

    /**
     * Distribution for returning random numbers out of a specified set of integers.
     */
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
