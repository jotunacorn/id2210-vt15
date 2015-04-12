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
package se.kth.swim.msg.net;

import se.kth.swim.msg.Alive;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-04-11.
 */
public class NetAlive extends NetMsg<Alive> {

    public NetAlive(NatedAddress src, NatedAddress dst, int counter) {
        super(src, dst, new Alive(counter));
    }

    private NetAlive(Header<NatedAddress> header, Alive content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetAlive(newHeader, getContent());
    }

}
