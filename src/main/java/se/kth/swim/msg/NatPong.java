package se.kth.swim.msg;

/**
 * Created by Mattias on 2015-05-07.
 */
public class NatPong {
    int pingNr;

    public int getPingNr() {
        return pingNr;
    }

    public NatPong(int pingNr){
        this.pingNr = pingNr;
    }
}
