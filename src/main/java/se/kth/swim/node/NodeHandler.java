package se.kth.swim.node;

import se.kth.swim.SwimComp;
import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Address;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.*;


public class NodeHandler {
    private static final int MESSAGE_SIZE = 10000;
    private static final int LAMBDA = 3;

    private NatedAddress selfAddress;

    private Map<Address, Integer> aliveNodes, suspectedNodes, deadNodes;
    private Map<Address, NatedAddress> addressMapping;
    private Map<Address, NodeInfo> sendBuffer;
    private List<Address> pingList;
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

    public void addAlive(NatedAddress address, int counter) {
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) { //Never add self to lists.
            return;
        }

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            if (aliveNodes.get(address.getBaseAdr()) < counter) { //If incarnation counter is lower, this is newer, update info.
                aliveNodes.put(address.getBaseAdr(), counter);
                addressMapping.put(address.getBaseAdr(), address);

                if (suspectedNodes.containsKey(address.getBaseAdr())) { //If node reported alive is suspected by us, remove it from suspected list.
                    suspectedNodes.remove(address.getBaseAdr());
                }

                if (sendBuffer.containsKey(address.getBaseAdr())) { //Also update counter in send queue
                    NodeInfo nodeInfo = sendBuffer.get(address.getBaseAdr());
                    nodeInfo.setIncarnationCounter(counter);
                    nodeInfo.setType(NodeInfo.Type.NEW);
                    sendBuffer.put(address.getBaseAdr(), nodeInfo);
                }
            }
        }
        else if (!deadNodes.containsKey(address.getBaseAdr())) { //Else add fresh entry
            aliveNodes.put(address.getBaseAdr(), counter);
            addressMapping.put(address.getBaseAdr(), address);
            sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, counter, NodeInfo.Type.NEW));
            addToPingList(address);
        }

        //printAliveNodes();
    }

    private void addToPingList(NatedAddress address) {
        int insertIndex = (int) (pingList.size() * Math.random());
        pingList.add(insertIndex, address);

    }

    /**
     * Copy of addAlive with <= on incarnation counter.
     */
    public void addDefinatelyAlive(NatedAddress address, int counter) {
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) { //Never add self to lists.
            return;
        }

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            if (aliveNodes.get(address.getBaseAdr()) <= counter) { //If incarnation counter is lower, this is newer, update info.
                aliveNodes.put(address.getBaseAdr(), counter);
                addressMapping.put(address.getBaseAdr(), address);

                if (suspectedNodes.containsKey(address.getBaseAdr())) { //If node reported alive is suspected by us, remove it from suspected list.
                    suspectedNodes.remove(address.getBaseAdr());
                }

                if (sendBuffer.containsKey(address.getBaseAdr())) { //Also update counter in send queue
                    NodeInfo nodeInfo = sendBuffer.get(address.getBaseAdr());
                    nodeInfo.setIncarnationCounter(counter);
                    nodeInfo.setType(NodeInfo.Type.NEW);
                    sendBuffer.put(address.getBaseAdr(), nodeInfo);
                }
            }
        }
        else if (!deadNodes.containsKey(address.getBaseAdr())) { //Else add fresh entry
            aliveNodes.put(address.getBaseAdr(), counter);
            addressMapping.put(address.getBaseAdr(), address);
            sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, counter, NodeInfo.Type.NEW));
            addToPingList(address);
        }
    }

    public void addSuspected(NatedAddress address, int counter) {
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) { //Never add self to lists.
            return;
        }

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            if (aliveNodes.get(address.getBaseAdr()) <= counter) {
                aliveNodes.put(address.getBaseAdr(), counter);
                addressMapping.put(address.getBaseAdr(), address);
                if (!suspectedNodes.containsKey(address.getBaseAdr())) {
                    sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, counter, NodeInfo.Type.SUSPECTED));
                }

                suspectedNodes.put(address.getBaseAdr(), counter);
            }
        }
        else if (!deadNodes.containsKey(address.getBaseAdr())) {
            aliveNodes.put(address.getBaseAdr(), counter);
            addressMapping.put(address.getBaseAdr(), address);
            suspectedNodes.put(address.getBaseAdr(), counter);
            sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, counter, NodeInfo.Type.SUSPECTED));
        }

        //printAliveNodes();
    }

    public void addSuspected(NatedAddress address) {
        int incarnationCounter = 0;

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            incarnationCounter = aliveNodes.get(address.getBaseAdr());
        }

        suspectedNodes.put(address.getBaseAdr(), incarnationCounter);
        addressMapping.put(address.getBaseAdr(), address);
        //printAliveNodes();
    }

    public void addDead(NatedAddress address, int counter) {
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) { //Never add self to lists.
            return;
        }

        aliveNodes.remove(address.getBaseAdr());
        suspectedNodes.remove(address.getBaseAdr());
        deadNodes.put(address.getBaseAdr(), counter);
        sendBuffer.put(address.getBaseAdr(), new NodeInfo(address, counter, NodeInfo.Type.DEAD));
        addressMapping.put(address.getBaseAdr(), address);
        //printAliveNodes();
    }

    public boolean addDead(NatedAddress address) {
        if (suspectedNodes.containsKey(address.getBaseAdr())) {
            addDead(address, 0);

            return true;
        }

        return false;
    }

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

    public Pong getPong(int pingNr, int incarnationCounter) {
        Map<Address, Integer> newNodesToSend = new HashMap<>();
        Map<Address, Integer> suspectedNodesToSend = new HashMap<>();
        Map<Address, Integer> deadNodesToSend = new HashMap<>();

        List<NodeInfo> bufferAsList = new ArrayList<>(sendBuffer.values());

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

            if (nodeInfo.getSendCounter() > LAMBDA * Math.max(1, Math.log(Math.max(1, aliveNodes.size())))) {
                sendBuffer.remove(nodeInfo.getAddress());
            }

            messageSizeCounter++;
        }
        return new Pong(convertToNated(newNodesToSend), convertToNated(suspectedNodesToSend), convertToNated(deadNodesToSend), pingNr, incarnationCounter);
    }

    public void printAliveNodes() {
        //SwimComp.log.info("{} Node status:\nAlive nodes({}): {}\nSuspected nodes: {}\nDead Nodes: {}", new Object[]{selfAddress.getId(), aliveNodes.size(),aliveNodes, suspectedNodes, deadNodes});
    }
    public Map<NatedAddress, Integer> convertToNated(Map<Address, Integer> nodes){
        Map<NatedAddress,Integer> natedAddresses = new HashMap<>();
        for(Address node : nodes.keySet()){
            NatedAddress address = addressMapping.get(node);
            if(address != null) {
                natedAddresses.put(address, nodes.get(node));
            }
        }
        return natedAddresses;
    }


    public Map<NatedAddress, Integer> getAliveNodes() {
        return convertToNated(aliveNodes);
    }

    public Map<NatedAddress, Integer> getDeadNodes() {
        return convertToNated(deadNodes);
    }

    public Map<NatedAddress, Integer> getSuspectedNodes() {
        return convertToNated(suspectedNodes);
    }
}