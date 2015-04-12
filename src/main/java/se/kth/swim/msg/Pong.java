package se.kth.swim.msg;

import se.kth.swim.NodeInfo;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by Mattias on 2015-04-11.
 */
public class Pong {

    private HashMap<NatedAddress, Integer> aliveNodes;
    private HashMap<NatedAddress, Integer> suspectedNodes;
    private HashMap<NatedAddress, Integer> deadNodes;
    private int pingNr;

    public Pong(HashMap<NatedAddress, Integer> aliveNodes, HashMap<NatedAddress, Integer> suspectedNodes, HashMap<NatedAddress, Integer> deadNodes, int pingNr) {
        this.aliveNodes = aliveNodes;
        this.suspectedNodes = suspectedNodes;
        this.deadNodes = deadNodes;
        this.pingNr = pingNr;
    }

    public HashMap<NatedAddress, Integer> getAliveNodes() {
        return aliveNodes;
    }

    public void setAliveNodes(HashMap<NatedAddress, Integer> aliveNodes) {
        this.aliveNodes = aliveNodes;
    }

    public HashMap<NatedAddress, Integer> getSuspectedNodes() {
        return suspectedNodes;
    }

    public void setSuspectedNodes(HashMap<NatedAddress, Integer> suspectedNodes) {
        this.suspectedNodes = suspectedNodes;
    }

    public HashMap<NatedAddress, Integer> getDeadNodes() {
        return deadNodes;
    }

    public void setDeadNodes(HashMap<NatedAddress, Integer> deadNodes) {
        this.deadNodes = deadNodes;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }
}

