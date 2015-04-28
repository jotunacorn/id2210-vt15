/**
 * This file is part of the Kompics P2P Framework.
 *
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * Kompics is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package se.kth.swim.croupier.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import se.kth.swim.croupier.CroupierSelectionPolicy;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class CroupierView<C extends Object> {

    private final int viewSize;
    private final BasicAddress selfAddress;
    private final HashMap<BasicAddress, CroupierViewEntry<C>> d2e;
    private final Random rand;

    private Comparator<CroupierViewEntry> comparatorByAge = new Comparator<CroupierViewEntry>() {
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

    public CroupierView(BasicAddress selfAddress, int viewSize, Random rand) {
        super();
        this.selfAddress = selfAddress;
        this.viewSize = viewSize;
        this.d2e = new HashMap<BasicAddress, CroupierViewEntry<C>>();
        this.rand = rand;
    }

    public void incrementDescriptorAges() {
        for (CroupierViewEntry entry : d2e.values()) {
            entry.getDescriptor().incrementAge();
        }
    }

    public NatedAddress selectPeerToShuffleWith(CroupierSelectionPolicy policy,
            boolean softmax, double temperature) {
        if (d2e.isEmpty()) {
            return null;
        }

        CroupierViewEntry selectedEntry = null;

        selectedEntry = generateRandomSample(1).get(0);
//        if (!softmax || policy == CroupierSelectionPolicy.RANDOM) {
//            if (policy == CroupierSelectionPolicy.TAIL) {
//                selectedEntry = Collections.max(d2e.values(), comparatorByAge);
//            } else if (policy == CroupierSelectionPolicy.HEALER) {
//                selectedEntry = Collections.max(d2e.values(), comparatorByAge);
//            } else if (policy == CroupierSelectionPolicy.RANDOM) {
//                selectedEntry = generateRandomSample(1).get(0);
//            } else {
//                throw new IllegalArgumentException("Invalid Croupier policy selected:" + policy);
//            }
//
//        } else {
//            ArrayList<CroupierViewEntry> entries = new ArrayList<CroupierViewEntry>(d2e.values());
//            if (policy == CroupierSelectionPolicy.TAIL) {
//                Collections.sort(entries, comparatorByAge);
//            } else if (policy == CroupierSelectionPolicy.HEALER) {
//                Collections.sort(entries, comparatorByAge);
//            } else {
//                throw new IllegalArgumentException("Invalid Croupier policy selected:" + policy);
//            }
//
//            int index = softMaxIndex(entries, temperature);
//            if (index == -1) {
//                selectedEntry = entries.get(entries.size() - 1);
//            } else {
//                selectedEntry = entries.get(index);
//            }
//        }
//
//        // TODO - by not removing a reference to the node I am shuffling with, we
//        // break the 'batched random walk' (Cyclon) behaviour. But it's more important
//        // to keep the graph connected.
//        if (d2e.size() >= viewSize) {
//            removeEntry(selectedEntry.getDescriptor().getSource().getBase());
//        }

        return selectedEntry.getDescriptor().getSource();
    }

    public Set<CroupierContainer<C>> initiatorCopySet(int count, NatedAddress destinationPeer) {
        List<CroupierViewEntry> randomEntries = generateRandomSample(count);
        Set<CroupierContainer<C>> descriptors = new HashSet<CroupierContainer<C>>();
        for (CroupierViewEntry cacheEntry : randomEntries) {
            cacheEntry.sentTo((BasicAddress)destinationPeer.getBaseAdr());
            descriptors.add(cacheEntry.getDescriptor().getCopy());
        }
        return descriptors;
    }

    public Set<CroupierContainer<C>> receiverCopySet(int count, NatedAddress destinationPeer) {
        List<CroupierViewEntry> randomEntries = generateRandomSample(count);
        Set<CroupierContainer<C>> descriptors = new HashSet<CroupierContainer<C>>();
        for (CroupierViewEntry cacheEntry : randomEntries) {
            cacheEntry.sentTo((BasicAddress)destinationPeer.getBaseAdr());
            descriptors.add(cacheEntry.getDescriptor().getCopy());
        }
        return descriptors;
    }

    public void selectToKeep(NatedAddress from, Set<CroupierContainer<C>> descriptors) {
        BasicAddress baseFrom = (BasicAddress)from.getBaseAdr();
        if (baseFrom.equals(selfAddress)) {
            return;
        }

        LinkedList<CroupierViewEntry> entriesSentToThisPeer = new LinkedList<CroupierViewEntry>();
        for (CroupierViewEntry cacheEntry : d2e.values()) {
            if (cacheEntry.wasSentTo(baseFrom)) {
                entriesSentToThisPeer.add(cacheEntry);
            }
        }
        //TODO Alex policy for removing the descriptor of the shuffle target - should it be the first i remove or last?
        if (d2e.containsKey(baseFrom)) {
            entriesSentToThisPeer.add(d2e.get(baseFrom));
        }

        for (CroupierContainer<C> descriptor : descriptors) {
            BasicAddress baseSrc = (BasicAddress)descriptor.getSource().getBaseAdr();
            if (selfAddress.equals(baseSrc)) {
                continue; // do not keep descriptor of self
            }
            if (d2e.containsKey(baseSrc)) {
                // we already have an entry for this peer. keep the youngest one

                CroupierViewEntry entry = d2e.get(baseSrc);
                if (entry.getDescriptor().getAge() > descriptor.getAge()) {
                    // we keep the lowest age descriptor
                    CroupierViewEntry newCVE = new CroupierViewEntry(descriptor);

                    //TODO Alex what is the policy about descriptors I sent and received from src
                    int index = entriesSentToThisPeer.indexOf(entry);
                    if (index != -1) {
                        entriesSentToThisPeer.set(index, newCVE);
                    }

                    removeEntry(baseSrc);
                    addEntry(newCVE);
                }
            } else if (d2e.size() < viewSize) {
                // fill an empty slot
                addEntry(new CroupierViewEntry(descriptor));
            } else {
                // replace one slot out of those sent to this peer
                CroupierViewEntry sentEntry = entriesSentToThisPeer.poll();
                if (sentEntry != null) {
                    removeEntry((BasicAddress)sentEntry.getDescriptor().getSource().getBaseAdr());
                    addEntry(new CroupierViewEntry(descriptor));
                }
            }
        }
    }

//-------------------------------------------------------------------	
    public final Set<CroupierContainer<C>> getAllCopy() {
        Set<CroupierContainer<C>> descriptors = new HashSet<CroupierContainer<C>>();
        for (CroupierViewEntry cacheEntry : d2e.values()) {
            descriptors.add(cacheEntry.getDescriptor().getCopy());
        }
        return descriptors;
    }

    private List<CroupierViewEntry> generateRandomSample(int n) {
        List<CroupierViewEntry> randomEntries = new ArrayList<CroupierViewEntry>();
        if (n >= d2e.size()) {
            //return a copy of all entries
            randomEntries.addAll(d2e.values());
            return randomEntries;
        }
        ArrayList<CroupierViewEntry> entries = new ArrayList<CroupierViewEntry>(d2e.values());
        // Don Knuth, The Art of Computer Programming, Algorithm S(3.4.2)
        int t = 0, m = 0, N = d2e.size();
        while (m < n) {
            int x = rand.nextInt(N - t);
            if (x < n - m) {
                randomEntries.add(entries.get(t));
                m += 1;
                t += 1;
            } else {
                t += 1;
            }
        }
        return randomEntries;
    }

    private void addEntry(CroupierViewEntry entry) {
        d2e.put((BasicAddress)entry.getDescriptor().getSource().getBaseAdr(), entry);
    }

    private boolean removeEntry(BasicAddress src) {
        return d2e.remove(src) != null;
    }

    public void timedOut(NatedAddress src) {
        removeEntry((BasicAddress)src.getBaseAdr());
    }

    public boolean isEmpty() {
        return d2e.isEmpty();
    }

    public int size() {
        return d2e.size();
    }

    //TODO Alex check if it matched to Abhi's soft max and replace
    private int softMaxIndex(ArrayList<CroupierViewEntry> entries, double temperature) {
        double rnd = rand.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val / temperature);
            total += values[i];
        }

        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                values[i] += values[i - 1];
            }
            // normalise the probability
            double normalisedReward = values[i] / total;
            if (normalisedReward >= rnd) {
                return i;
            }
        }
        return -1;
    }
}
