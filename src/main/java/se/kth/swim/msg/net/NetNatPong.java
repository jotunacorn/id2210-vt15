package se.kth.swim.msg.net;

import se.kth.swim.msg.NatPong;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-05-07.
 */
public class NetNatPong extends NetMsg<NatPong> {

    public NetNatPong(NatedAddress src, NatedAddress dst, int pingNr) {
        super(src, dst, new NatPong(pingNr));
    }

    private NetNatPong(Header<NatedAddress> header, NatPong content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetNatPong(newHeader, getContent());
    }

}
