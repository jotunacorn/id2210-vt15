/**
 * This file is part of the Kompics P2P Framework.
 * 
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS)
 * Copyright (C) 2009 Royal Institute of Technology (KTH)
 *
 * Kompics is free software; you can redistribute it and/or
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

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;

/**
 * The <code>RandomViewEntry</code> class represents an entry in a node's
 * randomView. It contains a node descriptor and it marks when and to what peer this
 * entry was last sent to. This information is used in the process of updating
 * the randomView during a shuffle, so that the first randomView entries removed are those
 * that were sent to the peer from whom we received the current shuffle
 * response.
 * 
 * @author Cosmin Arad <cosmin@sics.se>, Gautier Berthou
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class CroupierViewEntry<C extends Object> {

    public static enum Order implements Comparator<CroupierViewEntry> {
        ByAge() {
            @Override
            public int compare(CroupierViewEntry o1, CroupierViewEntry o2) {
                if (o1.getDescriptor().getAge() > o2.getDescriptor().getAge()) {
                    return 1;
                } else if (o1.getDescriptor().getAge() < o2.getDescriptor().getAge()) {
                    return -1;
                } else {
                    return 0;
                }
            }
        };
    }

    private final CroupierContainer<C> cc;
    private final long addedAt;
    private long sentAt;
    private final Set<BasicAddress> sentTo = new HashSet<BasicAddress>();

    public CroupierViewEntry(CroupierContainer<C> cc) {
        this.cc = cc;
        this.addedAt = System.currentTimeMillis();
        this.sentAt = 0;
    }


    public void sentTo(BasicAddress peer) {
        sentTo.add(peer);
        sentAt = System.currentTimeMillis();
    }

    public CroupierContainer<C> getDescriptor() {
        return cc;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public long getSentAt() {
        return sentAt;
    }

    public boolean wasSentTo(BasicAddress peer) {
        return sentTo == null ? false : sentTo.contains(peer);
    }
    
    @Override
    public String toString() {
        return cc.toString() + ": addedAt(" + addedAt + "): sentAt:(" + sentAt +")";
    }
}
