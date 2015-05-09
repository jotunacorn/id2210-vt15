package se.kth.swim.msg.net;

import se.kth.swim.msg.NewParentAlert;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Jotunn on 2015-05-09.
 */
public class NetNewParentAlert extends NetMsg<NewParentAlert>{
        public NetNewParentAlert(NatedAddress src, NatedAddress dst, NatedAddress newAddress) {
            super(src, dst, new NewParentAlert(newAddress));
        }

        private NetNewParentAlert(Header<NatedAddress> header, NewParentAlert content) {
            super(header, content);
        }

        @Override
        public NetMsg copyMessage(Header<NatedAddress> newHeader) {
            return new NetNewParentAlert(newHeader, getContent());
        }
}
