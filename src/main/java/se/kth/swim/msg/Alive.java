package se.kth.swim.msg;

/**
 * Created by Mattias on 2015-04-11.
 */
public class Alive {
    int incarnationCounter;

    public Alive(int incarnationCounter) {
        this.incarnationCounter = incarnationCounter;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }
}
