package com.ghostchu.btn.sparkle.module.tracker.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.net.InetAddress;
import java.sql.Timestamp;

@Entity
@Table(name = "tracker_peers",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"peerIp","peerId","torrentInfoHash"})},
        indexes = {@Index(columnList = "peerId"),@Index(columnList = "peerIp"), @Index(columnList = "torrentInfoHash"),
        @Index(columnList = "left"), @Index(columnList = "lastEvent")}
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@DynamicUpdate
public class TrackedPeer {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private InetAddress reqIp;
    @Column(nullable = false)
    private String peerId;
    @Column(nullable = false)
    private InetAddress peerIp;
    @Column(nullable = false)
    private Integer peerPort;
    @Column(nullable = false)
    private String torrentInfoHash;
//    @Column // too complex, we just give up it until somebody want to PR it
//    private byte[] torrentInfoHashSha1;
    @Column(nullable = false)
    private Long uploaded;
    @Column(nullable = false)
    private Long uploadedOffset;
    @Column(nullable = false)
    private Long downloaded;
    @Column(nullable = false)
    private Long downloadedOffset;
    @Column(nullable = false)
    private Long left;
    @Column(nullable = false)
    private PeerEvent lastEvent;
    @Column(nullable = false)
    private String userAgent;
    @Column(nullable = false)
    private Timestamp firstTimeSeen;
    @Column(nullable = false)
    private Timestamp lastTimeSeen;
}
