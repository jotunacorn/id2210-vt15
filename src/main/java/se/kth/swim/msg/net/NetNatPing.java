package se.kth.swim.msg.net;

import se.kth.swim.msg.NatPing;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-05-07.
 */
public class NetNatPing extends NetMsg<NatPing> {

    public NetNatPing(NatedAddress src, NatedAddress dst, int pingNr) {
        super(src, dst, new NatPing(pingNr));
    }

    private NetNatPing(Header<NatedAddress> header, NatPing content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetNatPing(newHeader, getContent());
    }


}
