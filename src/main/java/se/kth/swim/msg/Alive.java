package se.kth.swim.msg;

import se.sics.kompics.network.Address;

/**
 * Created by Mattias on 2015-04-11.
 */
public class Alive {
    int incarnationCounter;
    public Alive(int incarnationCounter){
        this.incarnationCounter = incarnationCounter;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }
}
