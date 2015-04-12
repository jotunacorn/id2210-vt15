package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by joakim on 2015-04-12.
 */
public class NodeInfo implements Serializable{
    int incarnationCounter;
    NatedAddress address;
    public NodeInfo(int counter, NatedAddress address){
        this.incarnationCounter = counter;
        this.address = address;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }

    public void setIncarnationCounter(int incarnationCounter) {
        this.incarnationCounter = incarnationCounter;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
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
