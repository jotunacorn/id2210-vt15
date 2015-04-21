package se.kth.swim.node;

import se.kth.swim.SwimComp;
import se.kth.swim.msg.Pong;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.*;


public class NodeHandler {
    private static final int MESSAGE_SIZE = 100000;
    private static final int LAMBDA = 3;

    private NatedAddress selfAddress;

    private Map<NatedAddress, Integer> aliveNodes, suspectedNodes, deadNodes;
    private Map<NatedAddress, NodeInfo> sendBuffer;

    public NodeHandler(NatedAddress selfAddress) {
        this.selfAddress = selfAddress;
        aliveNodes = new HashMap<>();
        suspectedNodes = new HashMap<>();
        deadNodes = new HashMap<>();
        sendBuffer = new HashMap<>();
    }

    public void addAlive(NatedAddress address, int counter) {
        if (address.equals(selfAddress)) { //Never add self to lists.
            return;
        }

        if (aliveNodes.containsKey(address)) {
            if (aliveNodes.get(address) < counter) { //If incarnation counter is lower, this is newer, update info.
                aliveNodes.put(address, counter);

                if (suspectedNodes.containsKey(address)) { //If node reported alive is suspected by us, remove it from suspected list.
                    suspectedNodes.remove(address);
                }

                if (sendBuffer.containsKey(address)) { //Also update counter in send queue
                    NodeInfo nodeInfo = sendBuffer.get(address);
                    nodeInfo.setIncarnationCounter(counter);
                    nodeInfo.setType(NodeInfo.Type.NEW);
                    sendBuffer.put(address, nodeInfo);
                }
            }
        }
        else if (!deadNodes.containsKey(address)) { //Else add fresh entry
            aliveNodes.put(address, counter);
            sendBuffer.put(address, new NodeInfo(address, counter, NodeInfo.Type.NEW));
        }
    }

    public void addSuspected(NatedAddress address, int counter) {
        if (address.equals(selfAddress)) { //Never add self to lists.
            return;
        }

        if (aliveNodes.containsKey(address)) {
            if (aliveNodes.get(address) < counter) {
                aliveNodes.put(address, counter);

                if (!suspectedNodes.containsKey(address)) {
                    sendBuffer.put(address, new NodeInfo(address, counter, NodeInfo.Type.SUSPECTED));
                }

                suspectedNodes.put(address, counter);
            }
        }
        else if (!deadNodes.containsKey(address)) {
            aliveNodes.put(address, counter);
            suspectedNodes.put(address, counter);
            sendBuffer.put(address, new NodeInfo(address, counter, NodeInfo.Type.SUSPECTED));
        }
    }

    public void addSuspected(NatedAddress address) {
        int incarnationCounter = 0;

        if (aliveNodes.containsKey(address)) {
            incarnationCounter = aliveNodes.get(address);
        }

        suspectedNodes.put(address, incarnationCounter);
    }

    public void addDead(NatedAddress address, int counter) {
        if (address.equals(selfAddress)) { //Never add self to lists.
            return;
        }

        aliveNodes.remove(address);
        suspectedNodes.remove(address);
        deadNodes.put(address, counter);
        sendBuffer.put(address, new NodeInfo(address, counter, NodeInfo.Type.DEAD));
    }

    public boolean addDead(NatedAddress address) {
        if (suspectedNodes.containsKey(address)) {
            addDead(address, 0);

            return true;
        }

        return false;
    }

    public NatedAddress getRandomAliveNode() {
        NatedAddress partnerAddress = null;

        if (aliveNodes.size() > 0) {
            List<NatedAddress> addresses = new ArrayList<>(aliveNodes.keySet());
            partnerAddress = addresses.get((int) (Math.random() * aliveNodes.size()));
        }

        return partnerAddress;
    }

    public Pong getPong(int pingNr, int incarnationCounter) {
        Map<NatedAddress, Integer> newNodesToSend = new HashMap<>();
        Map<NatedAddress, Integer> suspectedNodesToSend = new HashMap<>();
        Map<NatedAddress, Integer> deadNodesToSend = new HashMap<>();

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
                    newNodesToSend.put(nodeInfo.getAddress(), nodeInfo.getIncarnationCounter());
                    break;
                case SUSPECTED:
                    suspectedNodesToSend.put(nodeInfo.getAddress(), nodeInfo.getIncarnationCounter());
                    break;
                case DEAD:
                    deadNodesToSend.put(nodeInfo.getAddress(), nodeInfo.getIncarnationCounter());
                    break;
            }

            if (nodeInfo.getSendCounter() > LAMBDA * Math.log(Math.min(1, aliveNodes.size()))) {
                sendBuffer.remove(nodeInfo.getAddress());
            }

            messageSizeCounter++;
        }

        return new Pong(newNodesToSend, suspectedNodesToSend, deadNodesToSend, pingNr, incarnationCounter);
    }

    public void printAliveNodes() {
        SwimComp.log.info("{} Alive nodes: {}", new Object[]{selfAddress.getId(), aliveNodes});
    }

    public Map<NatedAddress, Integer> getAliveNodes() {
        return aliveNodes;
    }

}