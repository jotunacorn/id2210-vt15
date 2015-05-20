package se.kth.swim.msg.parentport;

import se.sics.kompics.KompicsEvent;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Set;

/**
 * Created by Jotunn on 2015-05-09.
 */
public class NewParentAlert implements KompicsEvent {
    Set<NatedAddress> parents;

    public NewParentAlert(Set<NatedAddress> address) {
        super();
        this.parents = address;
    }

    public Set<NatedAddress> getParents() {
        return parents;
    }

}
