package se.kth.swim.msg.parentport;

import se.sics.kompics.PortType;

/**
 * Created by Mattias on 2015-05-20.
 */
public class ParentPort extends PortType {
    {
        indication(NewParentAlert.class);
    }
}
