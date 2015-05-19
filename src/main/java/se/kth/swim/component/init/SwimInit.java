package se.kth.swim.component.init;

import se.kth.swim.component.SwimComp;
import se.sics.kompics.Init;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Set;

/**
 * Created by Mattias on 2015-04-21.
 */
public class SwimInit extends Init<SwimComp> {

    public final NatedAddress selfAddress;
    public final Set<NatedAddress> bootstrapNodes;
    public final NatedAddress aggregatorAddress;

    public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
        this.selfAddress = selfAddress;
        this.bootstrapNodes = bootstrapNodes;
        this.aggregatorAddress = aggregatorAddress;
    }
}
