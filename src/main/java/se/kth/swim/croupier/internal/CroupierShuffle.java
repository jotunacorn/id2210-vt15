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

import java.util.Set;
import java.util.UUID;
import se.sics.p2ptoolbox.util.identifiable.UUIDIdentifiable;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class CroupierShuffle {

    public static abstract class Basic implements UUIDIdentifiable {

        protected final UUID id;
        public final Set<CroupierContainer> publicNodes;
        public final Set<CroupierContainer> privateNodes;

        Basic(UUID id, Set<CroupierContainer> publicNodes, Set<CroupierContainer> privateNodes) {
            this.id = id;
            this.publicNodes = publicNodes;
            this.privateNodes = privateNodes;
            if(publicNodes.size() > 128 || privateNodes.size() > 128) {
                throw new RuntimeException("Croupier shuffle message is too large - limit yourself to 128 public nodes and 128 private nodes per shuffle");
            }
        }
        
        @Override
        public final UUID getId() {
            return id;
        }
    }
    
    public static class Request extends Basic {
        public Request(UUID id, Set<CroupierContainer> publicNodes, Set<CroupierContainer> privateNodes) {
            super(id, publicNodes, privateNodes);
        }
        
        @Override
        public String toString() {
            return "ShuffleRequest";
        }
        
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + (this.id != null ? this.id.hashCode() : 0);
            hash = 31 * hash + (this.publicNodes != null ? this.publicNodes.hashCode() : 0);
            hash = 31 * hash + (this.privateNodes != null ? this.privateNodes.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Request other = (Request) obj;
            if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
                return false;
            }
            if (this.publicNodes != other.publicNodes && (this.publicNodes == null || !this.publicNodes.equals(other.publicNodes))) {
                return false;
            }
            if (this.privateNodes != other.privateNodes && (this.privateNodes == null || !this.privateNodes.equals(other.privateNodes))) {
                return false;
            }
            return true;
        }
    }
    
    public static class Response extends Basic {
        public Response(UUID id, Set<CroupierContainer> publicNodes, Set<CroupierContainer> privateNodes) {
            super(id, publicNodes, privateNodes);
        }
        
        @Override
        public String toString() {
            return "ShuffleResponse";
        }
        
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + (this.id != null ? this.id.hashCode() : 0);
            hash = 31 * hash + (this.publicNodes != null ? this.publicNodes.hashCode() : 0);
            hash = 31 * hash + (this.privateNodes != null ? this.privateNodes.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Response other = (Response) obj;
            if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
                return false;
            }
            if (this.publicNodes != other.publicNodes && (this.publicNodes == null || !this.publicNodes.equals(other.publicNodes))) {
                return false;
            }
            if (this.privateNodes != other.privateNodes && (this.privateNodes == null || !this.privateNodes.equals(other.privateNodes))) {
                return false;
            }
            return true;
        }
    }
}
