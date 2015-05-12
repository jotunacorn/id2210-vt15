package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Set;

/**
 * Created by Jotunn on 2015-05-09.
 */
public class NewParentAlert {
    Set<NatedAddress> parents;
    public NewParentAlert(Set<NatedAddress> address){
        this.parents = address;
    }
    public Set<NatedAddress> getParents() {
        return parents;
    }

}
