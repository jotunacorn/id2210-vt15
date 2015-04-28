/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim.croupier.internal;

import se.kth.swim.croupier.util.OverlayHeaderImpl;
import se.kth.swim.msg.net.NetMsg;
import se.sics.kompics.network.Header;
import se.sics.kompics.network.Transport;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class CroupierShuffleNet {

    public static class Request extends NetMsg<CroupierShuffle.Request> {
        public Request(NatedAddress src, NatedAddress dst, int overlay, CroupierShuffle.Request content) {
            super(new OverlayHeaderImpl(new BasicHeader(src, dst, Transport.UDP), overlay), content);
        }
        public Request(Header<NatedAddress> header, CroupierShuffle.Request content) {
            super(header, content);
        }
        
        @Override
        public NetMsg copyMessage(Header<NatedAddress> newHeader) {
            return new Request(newHeader, getContent());
        }
    }
    
    public static class Response extends NetMsg<CroupierShuffle.Response> {
        public Response(NatedAddress src, NatedAddress dst, int overlay, CroupierShuffle.Response content) {
            super(new OverlayHeaderImpl(new BasicHeader(src, dst, Transport.UDP), overlay), content);
        }
        public Response(Header<NatedAddress> header, CroupierShuffle.Response content) {
            super(header, content);
        }
        
        @Override
        public NetMsg copyMessage(Header<NatedAddress> newHeader) {
            return new Response(newHeader, getContent());
        }
    }
}