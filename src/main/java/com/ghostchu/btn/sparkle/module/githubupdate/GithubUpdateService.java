package com.ghostchu.btn.sparkle.module.githubupdate;

import com.ghostchu.btn.sparkle.module.analyse.AnalyseService;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
import com.ghostchu.btn.sparkle.module.banhistory.BanHistoryService;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.util.IPUtil;
import inet.ipaddr.IPAddressString;
import jakarta.transaction.Transactional;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GithubUpdateService {
    private final BanHistoryRepository banHistoryRepository;
    private final AnalyseService analyseService;
    private final BanHistoryService banHistoryService;
    @Value("${service.githubruleupdate.access-token}")
    private String accessToken;
    @Value("${service.githubruleupdate.org-name}")
    private String orgName;
    @Value("${service.githubruleupdate.repo-name}")
    private String repoName;
    @Value("${service.githubruleupdate.branch-name}")
    private String branchName;
    @Value("${service.githubruleupdate.past-interval}")
    private long pastInterval;

    public GithubUpdateService(BanHistoryRepository banHistoryRepository, AnalyseService analyseService, BanHistoryService banHistoryService) {
        this.banHistoryRepository = banHistoryRepository;
        this.analyseService = analyseService;
        this.banHistoryService = banHistoryService;
    }


    @Scheduled(fixedDelayString = "${service.githubruleupdate.interval}")
    @Transactional
    public void githubRuleUpdate() throws IOException {
        log.info("开始更新 GitHub 同步规则存储库...");
        GitHub github = new GitHubBuilder().withOAuthToken(accessToken, orgName).build();
        var organization = github.getOrganization(orgName);
        if (organization == null) {
            throw new IllegalArgumentException("Organization " + orgName + " not found");
        }
        var repository = organization.getRepository(repoName);
        updateFile(repository, "untrusted-ips.txt", generateUntrustedIps().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "overdownload-ips.txt", generateOverDownloadIps().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "hp_torrent.txt", generateHpTorrents().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "dt_torrent.txt", generateDtTorrents().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "go.torrent dev 20181121.txt", generateBaiduNetdisk().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "0xde-0xad-0xbe-0xef.txt", generateDeadBeef().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "123pan.txt", generate123pan().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "random-peerid.txt", generateGopeedDev().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "dot1_v6_tagging.txt", generateDot1IPV6().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "strange_ipv6_block.txt", generateStrangeIPV6().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "high-risk-ips.txt", generateHighRiskIps().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "ipv6-dhcp-address.txt", generateDhcpAddress().getBytes(StandardCharsets.UTF_8));
    }

    private String generateDhcpAddress() {
        var strJoiner = new StringJoiner("\n");
        Set<String> ipSet = new HashSet<>();
        var result = banHistoryRepository.findByInsertTimeBetweenOrderByInsertTimeDescIPVx(pastTimestamp(), nowTimestamp(), 6);
        result
                .stream()
                .map(ban -> new IPAddressString(ban.getHostAddress()).getAddress())
                .filter(Objects::nonNull)
                .filter(ip -> ip.isIPv6() && ip.toFullString().contains(":0000:0000:0000:"))
                .forEach(ip -> ipSet.add(ip.toString()));
        ipSet.stream().sorted().forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateHighRiskIps() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameAndModuleLikeAndInsertTimeBetween("Transmission 2.94", "%ProgressCheatBlocker%", pastTimestamp(), nowTimestamp())
                .stream()
                .map(ban -> IPUtil.toString(ban.getPeerIp()))
                .distinct()
                .sorted()
                .forEach(strJoiner::add);
        banHistoryRepository.findDistinctByPeerClientNameAndModuleLikeAndInsertTimeBetween("aria2", "%ProgressCheatBlocker%", pastTimestamp(), nowTimestamp())
                .stream()
                .map(ban -> IPUtil.toString(ban.getPeerIp()))
                .distinct()
                .sorted()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateStrangeIPV6() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findByPeerIp(
                        "%2e0:61ff:fe%",
                        pastTimestamp(),
                        nowTimestamp()
                )
                .stream()
                .map(ban -> IPUtil.toString(ban.getPeerIp()))
                .distinct()
                .sorted()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }


    private String generateDot1IPV6() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findByPeerIp(
                        "%::1",
                        pastTimestamp(),
                        nowTimestamp()
                )
                .stream()
                .filter(banHistory -> banHistory.getPeerClientName().startsWith("Transmission"))
                .map(ban -> IPUtil.toString(ban.getPeerIp()))
                .distinct()
                .sorted()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateGopeedDev() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                        "%Gopeed dev%",
                        pastTimestamp(),
                        nowTimestamp()
                ).stream()
                .filter(banHistory -> !banHistory.getPeerId().toLowerCase(Locale.ROOT).startsWith("-gp"))
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generate123pan() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                        "%offline-download (devel) (anacrolix/torrent unknown)%",
                        pastTimestamp(),
                        nowTimestamp()
                )
                .stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateDeadBeef() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                        "%ޭ__%",
                        pastTimestamp(),
                        nowTimestamp()
                )
                .stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateBaiduNetdisk() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                        "go.torrent dev 20181121%",
                        pastTimestamp(),
                        nowTimestamp()
                )
                .stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }


    private String generateDtTorrents() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerIdLikeOrPeerClientNameLike(
                        "-DT%",
                        "dt/torrent%",
                        pastTimestamp(),
                        nowTimestamp()
                ).stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateHpTorrents() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerIdLikeOrPeerClientNameLike(
                        "-HP%",
                        "hp/torrent%",
                        pastTimestamp(),
                        nowTimestamp()

                )
                .stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private void updateFile(GHRepository repository, String file, byte[] content) {
        try {
            var oldFile = repository.getFileContent(file);
            var sha = oldFile != null ? oldFile.getSha() : null;

            if (oldFile != null) {
                @Cleanup
                var is = oldFile.read();
                var oldData = is.readAllBytes();
                if (Arrays.equals(content, oldData)) {
                    log.info("{}: 无需更新，跳过", file);
                    return;
                }
            }
            var commitResponse = repository.createContent()
                    .path(file)
                    .branch(branchName)
                    .message("[Sparkle] 自动更新 " + file)
                    .sha(sha)
                    .content(content)
                    .commit();
            var commit = commitResponse.getCommit();
            log.info("GitHub 同步规则 “{}” 更新结果：Sha: {}", file, commit.getSHA1());
        } catch (Exception e) {
            log.error("无法完成数据更新操作", e);
        }
    }

    private String generateUntrustedIps() {
        var list = analyseService.getUntrustedIPAddresses().stream().map(AnalysedRule::getIp).collect(Collectors.toList());
        list.addAll(banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween("aria2/1.34.0", pastTimestamp(), nowTimestamp()).stream().map(h -> h.getPeerIp().getHostAddress()).toList());
        return String.join("\n", analyseService.getUntrustedIPAddresses().stream().map(AnalysedRule::getIp).toList());
    }

    private String generateOverDownloadIps() {
        return String.join("\n", analyseService.getOverDownloadIPAddresses().stream().map(AnalysedRule::getIp).toList());
    }

    private Timestamp nowTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }

    private Timestamp pastTimestamp() {
        return new Timestamp(System.currentTimeMillis() - pastInterval);
    }
}
