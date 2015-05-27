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

import se.sics.kompics.Kompics;
import se.sics.kompics.simulation.SimulatorScheduler;
import se.sics.p2ptoolbox.simulator.run.LauncherComp;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimMain {

    private static final boolean USE_RANDOM_SEED = false; //Set to true if a random seed should be used, false if 1234 should be used.

    private static final int SIMULATION_LENGTH = 200; //Length of simulation, in cycles.

    private static final int NUMBER_OF_NODES = 50; //Number of nodes in the simulation.
    private static final int BOOTSTRAP_SIZE = 2; //Number of bootstrap nodes. (Parent count in NATED nodes is of this size too.)
    private static final boolean ALLOW_NAT = true; //Set to true if NATED nodes should be allowed.
    private static final int NATED_NODE_FRACTION = 4; //Set ratio of nated nodes. Value here will set every Nth node as nated. 2 = 50%, 3=33% ...

    private static final int KILL_SIZE = 20; //How many nodes to kill in total.
    private static final int KILL_INTERVAL = 10; //How often nodes should be killed, in cycles.
    private static final int FAILURE_AFTER = 100; //When in the simulation nodes should start failing, in cycles.

    public static void main(String[] args) {
        LauncherComp.scheduler = new SimulatorScheduler();

        /**
         * the 1234 is the simulation seed. The result of a scenario with the same seed should be deterministic. 
         * Should - unless you create Random() with no seed somewhere in your own classes.
         * If you create your own Random() they should use a seed based on the parents seed.
         * It can be the same seed or can be customized, eg: newSeed = a * oldSeed + b
         * When testing you code, you might want to run the scenario with different seeds.
         */
        //The seed limits the randomness but the tests are still to some extent random in their result.
        long seed = 1234L;

        if (USE_RANDOM_SEED) {
            seed = (long) (10000 * Math.random());
        }

        //Generic test cases. Specify parameters in the constants above.
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, SIMULATION_LENGTH, NUMBER_OF_NODES, BOOTSTRAP_SIZE, ALLOW_NAT, NATED_NODE_FRACTION);
        //LauncherComp.scenario = SwimScenario.withNodeDeaths(seed, SIMULATION_LENGTH, NUMBER_OF_NODES, BOOTSTRAP_SIZE, ALLOW_NAT, NATED_NODE_FRACTION, KILL_SIZE, KILL_INTERVAL, FAILURE_AFTER);
        //LauncherComp.scenario = SwimScenario.withLinkDeaths(seed, SIMULATION_LENGTH, NUMBER_OF_NODES, BOOTSTRAP_SIZE, ALLOW_NAT, NATED_NODE_FRACTION, KILL_SIZE, FAILURE_AFTER);

        /**
         * Tests without NATed nodes.
         */

        //Tests with for startup with different number of nodes. Message size is set in SwimComp.java
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 10, 2, false, 1);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 20, 2, false, 1);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 50, 2, false, 1);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 100, 2, false, 1);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 200, 2, false, 1);

        //Test for killing 20 nodes out of 50 (40%, message size is set in SwimComp.java)
        //LauncherComp.scenario = SwimScenario.withNodeDeaths(seed, 250, 50, 4, false, 1, 20, 0, 150);

        //Test for Churn killing 1 node every 10 iterations
        //LauncherComp.scenario = SwimScenario.withNodeDeaths(seed, 250, 50, 4, false, 1, 20,10, 150);

        //Test with link deaths, killing 20 links
        //LauncherComp.scenario = SwimScenario.withLinkDeaths(seed, 100, 50, 2, false, 1, 20, 50);

        /**
         * Tests with NATed nodes enabled.
         */

        //Tests with for startup with different number of nodes. Message size is set in SwimComp.java
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 10, 2, true, 2);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 20, 2, true, 2);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 150, 50, 2, true, 2);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 100, 2, true, 2);
        //LauncherComp.scenario = SwimScenario.simpleBoot(seed, 100, 200, 2, true, 2);

        //Test for killing 20 nodes out of 50 (40%, message size is set in SwimComp.java)
        LauncherComp.scenario = SwimScenario.withNodeDeaths(seed, 250, 50, 2, true, 2, 20, 0, 150);

        //Test for Churn killing 1 node every 10 iterations
        //LauncherComp.scenario = SwimScenario.withNodeDeaths(seed, 250, 50, 4, false, 1, 20,10, 150);

        //Test with link deaths, killing 20 links
        //LauncherComp.scenario = SwimScenario.withLinkDeaths(seed, 100, 50, 2, true, 2, 20, 50);

        try {
            LauncherComp.simulatorClientAddress = new BasicNatedAddress(new BasicAddress(InetAddress.getByName("127.0.0.1"), 30000, -1));
        } catch (UnknownHostException ex) {
            throw new RuntimeException("cannot create address for localhost");
        }

        Kompics.setScheduler(LauncherComp.scheduler);
        Kompics.createAndStart(LauncherComp.class, 1);
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex.getMessage());
        }

        //Assert.assertEquals(null, SwimSimulationResult.failureCause);
    }
}
