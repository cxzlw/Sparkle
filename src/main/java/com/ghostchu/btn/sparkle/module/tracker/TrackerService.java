package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.module.tracker.internal.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class TrackerService {

    private final TrackedPeerRepository trackedPeerRepository;
    private final TrackedTaskRepository trackedTaskRepository;

    private final long inactiveInterval;
    private final int maxPeersReturn;


    public TrackerService(TrackedPeerRepository trackedPeerRepository,
                          TrackedTaskRepository trackedTaskRepository,
                          @Value("${service.tracker.inactive-interval}") long inactiveInterval,
                          @Value("${service.tracker.max-peers-return}") int maxPeersReturn) {
        this.trackedPeerRepository = trackedPeerRepository;
        this.trackedTaskRepository = trackedTaskRepository;
        this.inactiveInterval = inactiveInterval;
        this.maxPeersReturn = maxPeersReturn;
    }

    @Scheduled(fixedDelayString = "${service.tracker.cleanup-interval}")
    @Transactional
    public void cleanup() {
        var count = trackedPeerRepository.deleteByLastTimeSeenLessThanEqual(new Timestamp(System.currentTimeMillis() - inactiveInterval));
        log.info("已清除 {} 个不活跃的 Peers", count);
    }

    @Async
    public void executeAnnounce(PeerAnnounce announce) {
        var trackedPeer = trackedPeerRepository.findByPeerIpAndPeerIdAndTorrentInfoHash(
                announce.peerIp(),
                announce.peerId(),
                announce.infoHash()
        ).orElse(new TrackedPeer(
                null,
                announce.reqIp(),
                announce.peerId(),
                announce.peerIp(),
                announce.peerPort(),
                announce.infoHash(),
                announce.uploaded(),
                announce.uploaded(),
                announce.downloaded(),
                announce.downloaded(),
                announce.left(),
                announce.peerEvent(),
                announce.userAgent(),
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis())
        ));
        if (trackedPeer.getDownloadedOffset() > announce.downloaded()
                || trackedPeer.getUploadedOffset() > announce.uploaded()) {
            trackedPeer.setDownloaded(trackedPeer.getDownloaded() + announce.downloaded());
            trackedPeer.setUploaded(trackedPeer.getUploaded() + announce.uploaded());
        } else {
            var downloadIncrease = announce.downloaded() - trackedPeer.getDownloaded();
            var uploadedIncrease = announce.uploaded() - trackedPeer.getUploaded();
            trackedPeer.setDownloaded(trackedPeer.getDownloaded() + downloadIncrease);
            trackedPeer.setUploaded(trackedPeer.getUploaded() + uploadedIncrease);
        }
        trackedPeer.setDownloadedOffset(announce.downloaded());
        trackedPeer.setUploadedOffset(announce.uploaded());
        trackedPeer.setUserAgent(announce.userAgent());
        trackedPeer.setLastTimeSeen(new Timestamp(System.currentTimeMillis()));
        trackedPeer.setLeft(announce.left());
        trackedPeer.setPeerPort(announce.peerPort());
        trackedPeer.setPeerIp(announce.peerIp());
        trackedPeer.setReqIp(announce.reqIp());
        var trackedTask = trackedTaskRepository.findByTorrentInfoHash(announce.infoHash()).orElse(new TrackedTask(
                null,
                announce.infoHash(),
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                0L, 0L
        ));
        // 检查 task 属性
        if (announce.peerEvent() == PeerEvent.STARTED) {
            // 新 task
            trackedTask.setLeechCount(trackedTask.getLeechCount() + 1);
        }
        if (announce.peerEvent() == PeerEvent.COMPLETED) {
            trackedTask.setDownloadedCount(trackedTask.getDownloadedCount() + 1);
        }
        trackedTaskRepository.save(trackedTask);
        if (announce.peerEvent() == PeerEvent.STOPPED && trackedPeer.getId() != null) {
            trackedPeerRepository.delete(trackedPeer);
        } else {
            trackedPeerRepository.save(trackedPeer);
        }
    }

    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) {
        List<Peer> v4 = new ArrayList<>();
        List<Peer> v6 = new ArrayList<>();
        AtomicInteger seeders = new AtomicInteger();
        AtomicInteger leechers = new AtomicInteger();
        long downloaded = 0;
        trackedPeerRepository.fetchPeersFromTorrent(torrentInfoHash, Math.min(numWant, maxPeersReturn))
                .forEach(peer -> {
                    if (peer.getPeerIp() instanceof Inet4Address ipv4) {
                        v4.add(new Peer(ipv4.getHostAddress(), peer.getPeerPort(), peer.getPeerId()));
                    }
                    if (peer.getPeerIp() instanceof Inet6Address ipv6) {
                        v6.add(new Peer(ipv6.getHostAddress(), peer.getPeerPort(), peer.getPeerId()));
                    }
                    if (peer.getLeft() == 0) {
                        seeders.incrementAndGet();
                    } else {
                        leechers.incrementAndGet();
                    }
                });
        var trackedTask = trackedTaskRepository.findByTorrentInfoHash(torrentInfoHash);
        if (trackedTask.isPresent()) {
            downloaded = trackedTask.get().getDownloadedCount();
        }
        return new TrackedPeerList(v4, v6, seeders.get(), leechers.get(), downloaded);
    }

    public ScrapeResponse scrape(byte[] torrentInfoHash){
        var seeders = trackedPeerRepository.countByTorrentInfoHashAndLeft(torrentInfoHash,0L);
        var leechers = trackedPeerRepository.countByTorrentInfoHashAndLeftNot(torrentInfoHash,0L);
        var downloaded = 0L;
        var optional = trackedTaskRepository.findByTorrentInfoHash(torrentInfoHash);
        if(optional.isPresent()){
            downloaded = optional.get().getDownloadedCount();
        }
        return new ScrapeResponse(seeders, leechers, downloaded);
    }

    public record ScrapeResponse(
            long seeders,
            long leechers,
            long downloaded
    ){}

    public record PeerAnnounce(
            byte[] infoHash,
            byte[] peerId,
            InetAddress reqIp,
            InetAddress peerIp,
            int peerPort,
            long uploaded,
            long downloaded,
            long left,
            PeerEvent peerEvent,
            String userAgent
    ) {

    }

    public record TrackedPeerList(
            List<Peer> v4,
            List<Peer> v6,
            long seeders,
            long leechers,
            long downloaded
    ) {
    }

    public record Peer(
            String ip,
            int port,
            byte[] peerId
    ) {
    }
}
