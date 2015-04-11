package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Set;

/**
 * Created by Mattias on 2015-04-11.
 */
public class Pong {

    private Set<NatedAddress> aliveNodes;
    private Set<NatedAddress> suspectedNodes;
    private Set<NatedAddress> deadNodes;
    private int pingNr;

    public Pong(Set<NatedAddress> aliveNodes, Set<NatedAddress> suspectedNodes, Set<NatedAddress> deadNodes, int pingNr) {
        this.aliveNodes = aliveNodes;
        this.suspectedNodes = suspectedNodes;
        this.deadNodes = deadNodes;
        this.pingNr = pingNr;
    }

    public Set<NatedAddress> getAliveNodes() {
        return aliveNodes;
    }

    public void setAliveNodes(Set<NatedAddress> aliveNodes) {
        this.aliveNodes = aliveNodes;
    }

    public Set<NatedAddress> getSuspectedNodes() {
        return suspectedNodes;
    }

    public void setSuspectedNodes(Set<NatedAddress> suspectedNodes) {
        this.suspectedNodes = suspectedNodes;
    }

    public Set<NatedAddress> getDeadNodes() {
        return deadNodes;
    }

    public void setDeadNodes(Set<NatedAddress> deadNodes) {
        this.deadNodes = deadNodes;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }
}

