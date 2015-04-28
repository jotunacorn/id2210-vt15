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

package se.kth.swim.croupier.util;

import se.sics.kompics.network.Address;
import se.sics.kompics.network.Transport;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.OverlayHeader;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class OverlayHeaderImpl<Adr extends Address> implements OverlayHeader<Adr> {
    private final BasicHeader<Adr> base;
    private final int overlayId;
    
    public OverlayHeaderImpl(BasicHeader<Adr> base, int overlayId) {
        this.base = base;
        this.overlayId = overlayId;
    }
    
    public int getOverlayId() {
        return overlayId;
    }

    public Adr getSource() {
        return base.getSource();
    }

    public Adr getDestination() {
        return base.getDestination();
    }

    public Transport getProtocol() {
        return base.getProtocol();
    }
}