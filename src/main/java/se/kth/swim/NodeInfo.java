package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.io.Serializable;

/**
 * Created by joakim on 2015-04-12.
 */
public class NodeInfo implements Serializable {

    enum Type {NEW, SUSPECTED, DEAD}

    private NatedAddress address;
    private int sendCounter;
    private Type type;

    public NodeInfo(NatedAddress address, int counter, Type type) {
        this.sendCounter = counter;
        this.address = address;
        this.type = type;
    }

    public int getSendCounter() {
        return sendCounter;
    }

    public void setSendCounter(int sendCounter) {
        this.sendCounter = sendCounter;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
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
