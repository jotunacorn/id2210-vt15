package se.kth.swim;

import se.sics.kompics.Init;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-05-17.
 */
public class NatTraversalInit extends Init<NatTraversalComp> {

    public final NatedAddress selfAddress;
    public final long seed;

    public NatTraversalInit(NatedAddress selfAddress, long seed) {
        this.selfAddress = selfAddress;
        this.seed = seed;
    }
}
