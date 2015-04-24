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

package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Map;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class Status {
    public int statusNr, receivedPings, sentPings;
    private Map<NatedAddress, Integer> aliveNodes, suspectedNodes, deadNodes;

    public Status(int statusNr, int receivedPings, int sentPings, Map<NatedAddress, Integer> aliveNodes, Map<NatedAddress, Integer> suspectedNodes, Map<NatedAddress, Integer> deadNodes) {
        this.statusNr = statusNr;
        this.receivedPings = receivedPings;
        this.sentPings = sentPings;
        this.aliveNodes = aliveNodes;
        this.suspectedNodes = suspectedNodes;
        this.deadNodes = deadNodes;
    }

    public int getStatusNr() {
        return statusNr;
    }

    public void setStatusNr(int statusNr) {
        this.statusNr = statusNr;
    }

    public int getReceivedPings() {
        return receivedPings;
    }

    public void setReceivedPings(int receivedPings) {
        this.receivedPings = receivedPings;
    }

    public int getSentPings() {
        return sentPings;
    }

    public void setSentPings(int sentPings) {
        this.sentPings = sentPings;
    }

    public Map<NatedAddress, Integer> getAliveNodes() {
        return aliveNodes;
    }

    public void setAliveNodes(Map<NatedAddress, Integer> aliveNodes) {
        this.aliveNodes = aliveNodes;
    }

    public Map<NatedAddress, Integer> getSuspectedNodes() {
        return suspectedNodes;
    }

    public void setSuspectedNodes(Map<NatedAddress, Integer> suspectedNodes) {
        this.suspectedNodes = suspectedNodes;
    }

    public Map<NatedAddress, Integer> getDeadNodes() {
        return deadNodes;
    }

    public void setDeadNodes(Map<NatedAddress, Integer> deadNodes) {
        this.deadNodes = deadNodes;
    }
}
