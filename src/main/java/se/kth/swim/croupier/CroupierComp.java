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
package se.kth.swim.croupier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.croupier.internal.CroupierContainer;
import se.kth.swim.croupier.internal.CroupierShuffle;
import se.kth.swim.croupier.internal.CroupierShuffleNet;
import se.kth.swim.croupier.internal.CroupierView;
import se.kth.swim.croupier.msg.CroupierDisconnected;
import se.kth.swim.croupier.msg.CroupierJoin;
import se.kth.swim.croupier.msg.CroupierSample;
import se.kth.swim.croupier.msg.CroupierUpdate;
import se.kth.swim.croupier.util.OverlayHeaderImpl;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class CroupierComp extends ComponentDefinition {

    private final static Logger log = LoggerFactory.getLogger(CroupierComp.class);

    Negative<CroupierControlPort> croupierControlPort = negative(CroupierControlPort.class);
    Negative<CroupierPort> croupierPort = negative(CroupierPort.class);
    Positive<Network> network = requires(Network.class);
    Positive<Timer> timer = requires(Timer.class);

    private NatedAddress self;
    private final CroupierConfig croupierConfig;
    private final String logPrefix;
    private final int overlayId;

    private List<NatedAddress> bootstrapNodes;
    private Object selfView;
    private CroupierView publicView;
    private CroupierView privateView;

    private UUID shuffleCycleId;
    private UUID shuffleTimeoutId;

    public CroupierComp(CroupierInit init) {
        this.self = init.self;
        this.croupierConfig = init.croupierConfig;
        this.overlayId = init.overlayId;
        this.logPrefix = "<oid:" + overlayId + ",nid:" + self.getBaseAdr().toString() + ">";
        this.bootstrapNodes = new ArrayList<NatedAddress>(init.bootstrapNodes);

        log.info("{} initiating with bootstrap nodes:{} ...", logPrefix, bootstrapNodes);

        this.selfView = null;
        this.shuffleCycleId = null;
        this.shuffleTimeoutId = null;

        Random rand = new Random(init.seed + overlayId);
        this.publicView = new CroupierView((BasicAddress) self.getBaseAdr(), croupierConfig.viewSize, rand);
        this.privateView = new CroupierView((BasicAddress) self.getBaseAdr(), croupierConfig.viewSize, rand);

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleJoin, croupierControlPort);
        subscribe(handleShuffleRequest, network);
        subscribe(handleShuffleResponse, network);
        subscribe(handleShuffleCycle, timer);
        subscribe(handleShuffleTimeout, timer);
        subscribe(handleUpdateView, croupierPort);
        subscribe(handleUpdateAddress, croupierPort);
    }

    Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", logPrefix);
            selfView = new Object();
            startShuffle();
        }

    };

    Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", logPrefix);
            stopShuffle();
        }

    };

    private void startShuffle() {
        if (selfView == null) {
            log.info("{} no self view - not shuffling", new Object[]{logPrefix});
            return;
        }
        if (!haveShufflePartners()) {
            log.info("{} no partners - not shuffling", new Object[]{logPrefix});
            return;
        }
        log.info("{} started shuffle", new Object[]{logPrefix});
        schedulePeriodicShuffle();
    }

    private void stopShuffle() {
        cancelPeriodicShuffle();
        log.info("{} stopped shuffle", new Object[]{logPrefix});
        trigger(new CroupierDisconnected(overlayId), croupierControlPort);
    }

    private boolean connected() {
        return shuffleCycleId != null;
    }

    private boolean haveShufflePartners() {
        return !bootstrapNodes.isEmpty() || !publicView.isEmpty() || !privateView.isEmpty();
    }

    Handler<CroupierJoin> handleJoin = new Handler<CroupierJoin>() {
        @Override
        public void handle(CroupierJoin join) {
            log.trace("{} {}", logPrefix, join);
            log.debug("{} joining using nodes:{}", logPrefix, join.peers);

            bootstrapNodes.addAll(join.peers);
            if (!connected()) {
                startShuffle();
            }
        }
    };

    Handler<CroupierUpdate.Address> handleUpdateAddress = new Handler<CroupierUpdate.Address>() {
        @Override
        public void handle(CroupierUpdate.Address update) {
            log.trace("{} {}", logPrefix, update);
            log.info("{} updating selfAddress:{}", new Object[]{logPrefix, update.selfAddress});

            self = (update.selfAddress == null ? self : update.selfAddress);

            if (!connected()) {
                startShuffle();
            }
        }
    };
    
    Handler<CroupierUpdate.View> handleUpdateView = new Handler<CroupierUpdate.View>() {
        @Override
        public void handle(CroupierUpdate.View update) {
            log.trace("{} {}", logPrefix, update);
            log.info("{} updating selfView:{}", new Object[]{logPrefix, update.selfView});

            selfView = (update.selfView == null ? selfView : update.selfView);

            if (!connected()) {
                startShuffle();
            }
        }
    };

    private NatedAddress selectPeerToShuffleWith(double temperature) {
        if (!bootstrapNodes.isEmpty()) {
            return bootstrapNodes.remove(0);
        }
        NatedAddress node = null;
        if (!publicView.isEmpty()) {
            node = publicView.selectPeerToShuffleWith(croupierConfig.policy, true, temperature);
        } else if (!privateView.isEmpty()) {
            node = privateView.selectPeerToShuffleWith(croupierConfig.policy, true, temperature);
        }
        return node;
    }

    Handler<ShuffleCycle> handleShuffleCycle = new Handler<ShuffleCycle>() {
        @Override
        public void handle(ShuffleCycle event) {
            log.trace("{} {}", logPrefix, event);
            log.debug("{} public view size:{}, private view size:{}, bootstrap nodes size:{}", new Object[]{logPrefix, publicView.size(), privateView.size(), bootstrapNodes.size()});

            if (!haveShufflePartners()) {
                log.warn("{} no shuffle partners - disconnected", logPrefix);
                stopShuffle();
                return;
            }

            if (!publicView.isEmpty() || !privateView.isEmpty()) {
                CroupierSample cs = new CroupierSample(overlayId, publicView.getAllCopy(), privateView.getAllCopy());
                log.info("{} publishing sample \n public nodes:{} \n private nodes:{}", new Object[]{logPrefix, cs.publicSample, cs.privateSample});
                trigger(cs, croupierPort);
            }

            NatedAddress peer = selectPeerToShuffleWith(croupierConfig.softMaxTemperature);
            if (peer == null || peer.getBaseAdr().equals(self.getBaseAdr())) {
                log.error("{} this should not happen - logic error selecting peer", logPrefix);
                throw new RuntimeException("Error selecting peer");
            }

            if (!peer.isOpen()) {
                log.debug("{} did not pick a public node for shuffling - public view size:{}", new Object[]{logPrefix, publicView.getAllCopy().size()});
            }

            // NOTE:
            publicView.incrementDescriptorAges();
            privateView.incrementDescriptorAges();

            Set<CroupierContainer> publicDescCopy = publicView.initiatorCopySet(croupierConfig.shuffleSize, peer);
            Set<CroupierContainer> privateDescCopy = privateView.initiatorCopySet(croupierConfig.shuffleSize, peer);

            if (self.isOpen()) {
                publicDescCopy.add(new CroupierContainer(self, selfView));
            } else {
                privateDescCopy.add(new CroupierContainer(self, selfView));
            }

            OverlayHeaderImpl<NatedAddress> requestHeader = new OverlayHeaderImpl(new BasicHeader(self, peer, Transport.UDP), overlayId);
            CroupierShuffle.Request requestContent = new CroupierShuffle.Request(UUID.randomUUID(), publicDescCopy, privateDescCopy);
            CroupierShuffleNet.Request request = new CroupierShuffleNet.Request(requestHeader, requestContent);
            log.trace("{} sending:{} to:{}", new Object[]{logPrefix, requestContent, peer});
            trigger(request, network);
            scheduleShuffleTimeout(peer);
        }
    };

    Handler handleShuffleRequest = new Handler<CroupierShuffleNet.Request>() {

        @Override
        public void handle(CroupierShuffleNet.Request request) {
            OverlayHeaderImpl<NatedAddress> header = (OverlayHeaderImpl) request.getHeader();
            if (header.getOverlayId() != overlayId) {
                log.error("{} message with header:{} not belonging to croupier overlay:{}", new Object[]{logPrefix, header, overlayId});
                throw new RuntimeException("message not belonging to croupier overlay");
            }
            NatedAddress reqSrc = request.getHeader().getSource();
            if (self.getBaseAdr().equals(reqSrc.getBaseAdr())) {
                log.error("{} Tried to shuffle with myself", logPrefix);
                throw new RuntimeException("tried to shuffle with myself");
            }
            log.trace("{} received:{} from:{}", new Object[]{logPrefix, request, reqSrc});
            if (selfView == null) {
                log.warn("{} not ready to shuffle - no self view available - {} tried to shuffle with me",
                        logPrefix, reqSrc);
                return;
            }

            log.debug("{} received from:{} \n public nodes:{} \n private nodes:{}",
                    new Object[]{logPrefix, request.getHeader().getSource(), request.getContent().publicNodes, request.getContent().privateNodes});

            publicView.incrementDescriptorAges();
            privateView.incrementDescriptorAges();

            Set<CroupierContainer> publicDescCopy = publicView.receiverCopySet(croupierConfig.shuffleSize, reqSrc);
            Set<CroupierContainer> privateDescCopy = privateView.receiverCopySet(croupierConfig.shuffleSize, reqSrc);
            if (self.isOpen()) {
                publicDescCopy.add(new CroupierContainer(self, selfView));
            } else {
                privateDescCopy.add(new CroupierContainer(self, selfView));
            }

            OverlayHeaderImpl<NatedAddress> responseHeader = new OverlayHeaderImpl(new BasicHeader(self, reqSrc, Transport.UDP), overlayId);
            CroupierShuffle.Response responseContent = new CroupierShuffle.Response(request.getContent().getId(), publicDescCopy, privateDescCopy);
            CroupierShuffleNet.Response response = new CroupierShuffleNet.Response(responseHeader, responseContent);

            log.trace("{} sending:{} to:{}", new Object[]{logPrefix, responseContent, reqSrc});
            trigger(response, network);

            publicView.selectToKeep(reqSrc, request.getContent().publicNodes);
            privateView.selectToKeep(reqSrc, request.getContent().privateNodes);
            if (!connected() && haveShufflePartners()) {
                startShuffle();
            }
        }
    };

    Handler handleShuffleResponse = new Handler<CroupierShuffleNet.Response>() {

                @Override
                public void handle(CroupierShuffleNet.Response response) {
                    OverlayHeaderImpl<NatedAddress> header = (OverlayHeaderImpl)response.getHeader();
                    if (header.getOverlayId() != overlayId) {
                        log.error("{} message with header:{} not belonging to croupier overlay:{}", new Object[]{logPrefix, header, overlayId});
                        throw new RuntimeException("message not belonging to croupier overlay");
                    }
                    NatedAddress respSrc = response.getHeader().getSource();
                    if (self.getBaseAdr().equals(respSrc.getBaseAdr())) {
                        log.error("{} Tried to shuffle with myself", logPrefix);
                        throw new RuntimeException("tried to shuffle with myself");
                    }
                    log.trace("{} received:{} from:{}", new Object[]{logPrefix, response, respSrc});

                    if (shuffleTimeoutId == null) {
                        log.debug("{} req:{}  already timed out", new Object[]{logPrefix, response.getContent().getId(), respSrc});
                        return;
                    }

                    publicView.selectToKeep(respSrc, response.getContent().publicNodes);
                    privateView.selectToKeep(respSrc, response.getContent().privateNodes);
                    cancelShuffleTimeout();
                }
            };

    Handler<ShuffleTimeout> handleShuffleTimeout = new Handler<ShuffleTimeout>() {
        @Override
        public void handle(ShuffleTimeout timeout) {
            log.info("{} node:{} timed out", logPrefix, timeout.dest);

            shuffleTimeoutId = null;
            if (timeout.dest.isOpen()) {
                publicView.timedOut(timeout.dest);
            } else {
                privateView.timedOut(timeout.dest);
            }
        }
    };

    private void schedulePeriodicShuffle() {
        if (shuffleCycleId != null) {
            log.warn("{} double starting periodic shuffle", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(croupierConfig.shufflePeriod, croupierConfig.shufflePeriod);
        ShuffleCycle sc = new ShuffleCycle(spt);
        spt.setTimeoutEvent(sc);
        shuffleCycleId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicShuffle() {
        if (shuffleCycleId == null) {
            log.warn("{} double stopping periodic shuffle", logPrefix);
            return;
        }
        CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(shuffleCycleId);
        shuffleCycleId = null;
        trigger(cpt, timer);
    }

    private void scheduleShuffleTimeout(NatedAddress dest) {
        if (shuffleTimeoutId != null) {
            log.warn("{} double starting shuffle timeout", logPrefix);
            return;
        }
        ScheduleTimeout spt = new ScheduleTimeout(croupierConfig.shufflePeriod / 2);
        ShuffleTimeout sc = new ShuffleTimeout(spt, dest);
        spt.setTimeoutEvent(sc);
        shuffleTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelShuffleTimeout() {
        if (shuffleTimeoutId == null) {
            log.warn("{} double stopping shuffle timeout", logPrefix);
        }
        CancelTimeout cpt = new CancelTimeout(shuffleTimeoutId);
        shuffleTimeoutId = null;
        trigger(cpt, timer);
    }

    public static class CroupierInit extends Init<CroupierComp> {

        public final NatedAddress self;
        public final List<NatedAddress> bootstrapNodes;
        public final long seed;
        public final CroupierConfig croupierConfig;
        public final int overlayId;

        public CroupierInit(NatedAddress self, List<NatedAddress> bootstrapNodes, long seed, CroupierConfig croupierConfig, int overlayId) {
            this.self = self;
            this.bootstrapNodes = bootstrapNodes;
            this.seed = seed;
            this.croupierConfig = croupierConfig;
            this.overlayId = overlayId;
        }
    }

    public class ShuffleCycle extends Timeout {

        public ShuffleCycle(SchedulePeriodicTimeout request) {
            super(request);
        }

        @Override
        public String toString() {
            return "SHUFFLE_CYCLE";
        }
    }

    public class ShuffleTimeout extends Timeout {

        public final NatedAddress dest;

        public ShuffleTimeout(ScheduleTimeout request, NatedAddress dest) {
            super(request);
            this.dest = dest;
        }

        @Override
        public String toString() {
            return "SHUFFLE_TIMEOUT";
        }
    }
}
