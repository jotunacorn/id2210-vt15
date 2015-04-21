package se.kth.swim.node;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.io.Serializable;

/**
 * Created by joakim on 2015-04-12.
 */
public class NodeInfo implements Serializable {

    enum Type {NEW, SUSPECTED, DEAD}

    private NatedAddress address;
    private int sendCounter;
    private int incarnationCounter;
    private Type type;

    public NodeInfo(NatedAddress address, int incarnationCounter, Type type) {
        this.address = address;
        this.sendCounter = 0;
        this.incarnationCounter = incarnationCounter;
        this.type = type;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
    }

    public int getSendCounter() {
        return sendCounter;
    }

    public void setSendCounter(int sendCounter) {
        this.sendCounter = sendCounter;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }

    public void setIncarnationCounter(int incarnationCounter) {
        this.incarnationCounter = incarnationCounter;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isNew() {
        return type == Type.NEW;
    }

    public boolean isSuspected() {
        return type == Type.SUSPECTED;
    }

    public boolean isDead() {
        return type == Type.DEAD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeInfo nodeInfo = (NodeInfo) o;

        return !(address != null ? !address.equals(nodeInfo.address) : nodeInfo.address != null);

    }

    @Override
    public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }
}
