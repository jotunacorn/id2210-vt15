package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Map;

/**
 * Created by Mattias on 2015-04-11.
 */
public class Pong {

    private Map<NatedAddress, Integer> newNodes;
    private Map<NatedAddress, Integer> suspectedNodes;
    private Map<NatedAddress, Integer> deadNodes;
    private int pingNr;
    private int incarnationCounter;

    public Pong(Map<NatedAddress, Integer> newNodes, Map<NatedAddress, Integer> suspectedNodes, Map<NatedAddress, Integer> deadNodes, int pingNr, int incarnationCounter) {
        this.newNodes = newNodes;
        this.suspectedNodes = suspectedNodes;
        this.deadNodes = deadNodes;
        this.pingNr = pingNr;
        this.incarnationCounter = incarnationCounter;
    }

    public Map<NatedAddress, Integer> getNewNodes() {
        return newNodes;
    }

    public void setNewNodes(Map<NatedAddress, Integer> newNodes) {
        this.newNodes = newNodes;
    }

    public Map<NatedAddress, Integer> getSuspectedNodes() {
        return suspectedNodes;
    }

    public void setSuspectedNodes(Map<NatedAddress, Integer> suspectedNodes) {
        this.suspectedNodes = suspectedNodes;
    }

    public Map<NatedAddress, Integer> getDeadNodes() {
        return deadNodes;
    }

    public void setDeadNodes(Map<NatedAddress, Integer> deadNodes) {
        this.deadNodes = deadNodes;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }

    public void setIncarnationCounter(int incarnationCounter) {
        this.incarnationCounter = incarnationCounter;
    }
}

