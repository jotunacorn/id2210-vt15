package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Jotunn on 2015-05-09.
 */
public class NewParentAlert {
    NatedAddress address;

    public NewParentAlert(NatedAddress address){
        this.address = address;
    }
    public NatedAddress getAddress() {
        return address;
    }

}
