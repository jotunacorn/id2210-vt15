package se.kth.swim.node;

import se.kth.swim.component.SwimComp;
import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Address;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.*;


public class NodeHandler {
    private static final int MESSAGE_SIZE = 10000; //How many nodes piggybacked in each pong.
    private static final int LAMBDA = 3; //How many times the node change is piggybacked. Lambda * log(n)

    private NatedAddress selfAddress;

    //Maps containing our nodes. Key is address, value is incarnation counter.
    private Map<Address, Integer> aliveNodes, suspectedNodes, deadNodes;

    //Keeps the mapping between Address and NatedAddress.
    //Because NatedAddress hashcode changes when parents change we couldnt use it as a key to the previous map.
    private Map<Address, NatedAddress> addressMapping;

    //Sendbuffer holding the recent node changes that are to be piggybacked.
    private Map<Address, NodeInfo> sendBuffer;

    //List of nodes to ping. Used for the round robin pinging.
    private List<Address> pingList;

    //Current index in list of nodes to ping in round robin.
    private int pingIndex;

    public NodeHandler(NatedAddress selfAddress) {
        this.selfAddress = selfAddress;
        aliveNodes = new HashMap<>();
        addressMapping = new HashMap<>();
        suspectedNodes = new HashMap<>();
        deadNodes = new HashMap<>();
        sendBuffer = new HashMap<>();
        pingList = new ArrayList<>();
    }

    /**
     * Called to add a node to the alive list.
     * Will take incarnation counter into account and priorities between alive/suspected/dead nodes.
     */
    public void addAlive(NatedAddress address, int incarnationCounter) {
        //Never add self to lists.
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        //If the node already is in the alive list, maybe we want to update it.
        if (aliveNodes.containsKey(address.getBaseAdr())) {
            //If incarnation counter is lower, this is newer, update info.
            if (aliveNodes.get(address.getBaseAdr()) < incarnationCounter) {
                aliveNodes.put(address.getBaseAdr(), incarnationCounter);
                addressMapping.put(address.getBaseAdr(), address);

                //If node reported alive is suspected by us, remove it from suspected list.
                if (suspectedNodes.containsKey(address.getBaseAdr())) {
                    suspectedNodes.remove(address.getBaseAdr());
                }

                //Also update counter in send queue
                if (sendBuffer.containsKey(address.getBaseAdr())) {
                    NodeInfo nodeInfo = sendBuffer.get(address.getBaseAdr());
                    nodeInfo.setIncarnationCounter(incarnationCounter);
                    nodeInfo.setType(NodeInfo.Type.NEW);
                    sendBuffer.put(address.getBaseAdr(), nodeInfo);
                }
            }
        }
        //If the node is not already in our alive list, but not declared dead, add it to alive list.
        else if (!deadNodes.containsKey(address.getBaseAdr())) {
            aliveNodes.put(address.getBaseAdr(), incarnationCounter);
            addressMapping.put(address.getBaseAdr(), address);

            //Also add it to send buffer because it is a new node.
            sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, incarnationCounter, NodeInfo.Type.NEW));

            //And add it to the round robin ping list.
            addToPingList(address);
        }
    }

    /**
     * Copy of addAlive with <= on incarnation counter.
     */
    public void addDefinatelyAlive(NatedAddress address, int incarnationCounter) {
        //Never add self to lists.
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        //If the node already is in the alive list, maybe we want to update it.
        if (aliveNodes.containsKey(address.getBaseAdr())) {
            //If incarnation counter is lower, this is newer, update info.
            if (aliveNodes.get(address.getBaseAdr()) <= incarnationCounter) {
                aliveNodes.put(address.getBaseAdr(), incarnationCounter);
                addressMapping.put(address.getBaseAdr(), address);

                //If node reported alive is suspected by us, remove it from suspected list.
                if (suspectedNodes.containsKey(address.getBaseAdr())) {
                    suspectedNodes.remove(address.getBaseAdr());
                }

                //Also update counter in send queue
                if (sendBuffer.containsKey(address.getBaseAdr())) {
                    NodeInfo nodeInfo = sendBuffer.get(address.getBaseAdr());
                    nodeInfo.setIncarnationCounter(incarnationCounter);
                    nodeInfo.setType(NodeInfo.Type.NEW);
                    sendBuffer.put(address.getBaseAdr(), nodeInfo);
                }
            }
        }
        //If the node is not already in our alive list, but not declared dead, add it to alive list.
        else if (!deadNodes.containsKey(address.getBaseAdr())) {
            aliveNodes.put(address.getBaseAdr(), incarnationCounter);
            addressMapping.put(address.getBaseAdr(), address);

            //Also add it to send buffer because it is a new node.
            sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, incarnationCounter, NodeInfo.Type.NEW));

            //And add it to the round robin ping list.
            addToPingList(address);
        }
    }

    /**
     * Will add a node to the send buffer as a new node.
     * Used when receiving new parents and we want to propagate them to other nodes.
     */
    public void addNodeToSendBuffer(NatedAddress address, int incarnationCounter) {
        sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, incarnationCounter, NodeInfo.Type.NEW));
    }

    /**
     * Helper function.
     * Will add a node to a random position in the round robin ping list.
     */
    private void addToPingList(NatedAddress address) {
        int insertIndex = (int) (pingList.size() * Math.random());
        pingList.add(insertIndex, address);
    }

    /**
     * Called to add a node to the suspected list.
     * Will take incarnation counter into account and priorities between alive/suspected/dead nodes.
     */
    public void addSuspected(NatedAddress address, int incarnationCounter) {
        //Never add self to lists.
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        //If node is in the alive list.
        if (aliveNodes.containsKey(address.getBaseAdr())) {
            //If incarnation counter is lower, this is newer, update info.
            if (aliveNodes.get(address.getBaseAdr()) <= incarnationCounter) {
                aliveNodes.put(address.getBaseAdr(), incarnationCounter);
                addressMapping.put(address.getBaseAdr(), address);

                //If this node is not already suspected, also propagate it by adding it to the send buffer.
                if (!suspectedNodes.containsKey(address.getBaseAdr())) {
                    sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, incarnationCounter, NodeInfo.Type.SUSPECTED));
                }

                suspectedNodes.put(address.getBaseAdr(), incarnationCounter);
            }
        }
        //If node is not in alive list, and not dead add it to the alive list and the suspected list.
        else if (!deadNodes.containsKey(address.getBaseAdr())) {
            aliveNodes.put(address.getBaseAdr(), incarnationCounter);
            addressMapping.put(address.getBaseAdr(), address);
            suspectedNodes.put(address.getBaseAdr(), incarnationCounter);

            //Add node to send buffer in order to propagate it.
            sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, incarnationCounter, NodeInfo.Type.SUSPECTED));
        }
    }

    /**
     * Adds a node to the suspected list. Will get the incarnation counter from alive list,
     * as we dont get incarnation counter when we suspect nodes from ping timeout.
     */
    public void addSuspected(NatedAddress address) {
        int incarnationCounter = 0;

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            incarnationCounter = aliveNodes.get(address.getBaseAdr());
        }

        suspectedNodes.put(address.getBaseAdr(), incarnationCounter);
        addressMapping.put(address.getBaseAdr(), address);

        //Add node to send buffer in order to propagate it.
        sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, incarnationCounter, NodeInfo.Type.SUSPECTED));
    }

    /**
     * Called to add a node to the dead list. As dead messages have priority over
     * suspected and alive, incarnation counters are not compared.
     */
    public void addDead(NatedAddress address, int incarnationCounter) {
        //Never add self to lists.
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        aliveNodes.remove(address.getBaseAdr());
        suspectedNodes.remove(address.getBaseAdr());
        deadNodes.put(address.getBaseAdr(), incarnationCounter);
        addressMapping.put(address.getBaseAdr(), address);

        //Add node to send buffer in order to propagate it.
        sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, incarnationCounter, NodeInfo.Type.DEAD));
    }

    /**
     * Called to add a node to the dead list when we dont have the incarnation counter, from timeout.
     * Returns true if the node was successfully added to the dead list, used for logging.
     */
    public boolean addDead(NatedAddress address) {
        //Will only add the node to the dead list if it already was suspected.
        if (suspectedNodes.containsKey(address.getBaseAdr())) {
            addDead(address, 0);

            return true;
        }

        return false;
    }

    /**
     * Returns a random node from the alive list.
     * Will return nodes in a round robin fashion, as described in the report.
     */
    public NatedAddress getRandomAliveNode() {
        if (pingList.isEmpty() || pingIndex >= pingList.size()) {
            pingList.clear();
            pingList.addAll(aliveNodes.keySet());
            Collections.shuffle(pingList);
            pingIndex = 0;
        }
        if (pingList.isEmpty()) {
            return null;
        }
        Address address = pingList.get(pingIndex);
        NatedAddress natedAddress = addressMapping.get(address);
        pingIndex++;
        return natedAddress;
    }

    /**
     * Generates a pong message with piggyback information.
     */
    public Pong getPong(int pingNr, int incarnationCounter) {
        Map<Address, Integer> newNodesToSend = new HashMap<>();
        Map<Address, Integer> suspectedNodesToSend = new HashMap<>();
        Map<Address, Integer> deadNodesToSend = new HashMap<>();

        List<NodeInfo> bufferAsList = new ArrayList<>(sendBuffer.values());

        //Sort the send buffer so we prioritize items that are propagated the least amount of times.
        Collections.sort(bufferAsList, new Comparator<NodeInfo>() {
            @Override
            public int compare(NodeInfo o1, NodeInfo o2) {
                if (o1.getSendCounter() > o2.getSendCounter()) {
                    return 1;
                }
                else if (o1.getSendCounter() < o2.getSendCounter()) {
                    return -1;
                }
                else {
                    return 0;
                }
            }
        });

        int messageSizeCounter = 0;

        //Get nodes from the send buffer, add them to the appropriate list and update sendcounters.
        for (NodeInfo nodeInfo : bufferAsList) {
            if (messageSizeCounter > MESSAGE_SIZE) {
                break;
            }

            nodeInfo.setSendCounter(nodeInfo.getSendCounter() + 1);

            switch (nodeInfo.getType()) {

                case NEW:
                    newNodesToSend.put(nodeInfo.getAddress().getBaseAdr(), nodeInfo.getIncarnationCounter());
                    break;
                case SUSPECTED:
                    suspectedNodesToSend.put(nodeInfo.getAddress().getBaseAdr(), nodeInfo.getIncarnationCounter());
                    break;
                case DEAD:
                    deadNodesToSend.put(nodeInfo.getAddress().getBaseAdr(), nodeInfo.getIncarnationCounter());
                    break;
            }

            //If node was propagated enough times, remove it from the send buffer.
            if (nodeInfo.getSendCounter() > LAMBDA * Math.max(1, Math.log(Math.max(1, aliveNodes.size())))) {
                sendBuffer.remove(nodeInfo.getAddress());
            }

            messageSizeCounter++;
        }
        return new Pong(convertToNated(newNodesToSend), convertToNated(suspectedNodesToSend), convertToNated(deadNodesToSend), pingNr, incarnationCounter);
    }

    /**
     * Helper function, will print all alive, suspected and dead nodes.
     */
    public void printAliveNodes() {
        SwimComp.log.info("{} Node status:\nAlive nodes({}): {}\nSuspected nodes: {}\nDead Nodes: {}", new Object[]{selfAddress.getId(), aliveNodes.size(), aliveNodes, suspectedNodes, deadNodes});
    }

    /**
     * Helper function, will return a map of NatedAddresses, based on the Address input.
     */
    public Map<NatedAddress, Integer> convertToNated(Map<Address, Integer> nodes) {
        Map<NatedAddress, Integer> natedAddresses = new HashMap<>();
        for (Address node : nodes.keySet()) {
            NatedAddress address = addressMapping.get(node);
            if (address != null) {
                natedAddresses.put(address, nodes.get(node));
            }
        }
        return natedAddresses;
    }

    /**
     * Helper function will return a map of all alive nodes.
     */
    public Map<NatedAddress, Integer> getAliveNodes() {
        return convertToNated(aliveNodes);
    }

    /**
     * Helper function will return a map of all dead nodes.
     */
    public Map<NatedAddress, Integer> getDeadNodes() {
        return convertToNated(deadNodes);
    }


    /**
     * Helper function will return a map of all suspected nodes.
     */
    public Map<NatedAddress, Integer> getSuspectedNodes() {
        return convertToNated(suspectedNodes);
    }

}