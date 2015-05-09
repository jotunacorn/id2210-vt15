package se.kth.swim.msg;

/**
 * Created by Mattias on 2015-05-07.
 */
public class NatPing {
    int pingNr;

    public int getPingNr() {
        return pingNr;
    }

    public NatPing(int pingNr){
        this.pingNr = pingNr;
    }
}
