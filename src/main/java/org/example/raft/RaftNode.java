package org.example.raft;

import org.example.raft.cluster.ClusterConfig;
import org.example.raft.log.LogEntry;
import org.example.raft.protocol.AppendEntriesRequest;
import org.example.raft.protocol.AppendEntriesResponse;
import org.example.raft.protocol.RequestVoteRequest;
import org.example.raft.protocol.RequestVoteResponse;
import org.example.raft.transport.RaftTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Реализация ноды кластера
 */
public class RaftNode implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaftNode.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(150);
    private static final Duration MIN_ELECTION_TIMEOUT = Duration.ofMillis(400);
    private static final Duration MAX_ELECTION_TIMEOUT = Duration.ofMillis(800);

    private final ClusterConfig config; // конфигурация кластера
    private final RaftTransport transport; // транспорт для RPC
    private final StateMachine stateMachine;
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2); // планировщик для таймеров выборов
    private final ExecutorService executor = Executors.newCachedThreadPool(); // пул для асинхронных задач
    private final List<LogEntry> log = new ArrayList<>();
    private final ConcurrentMap<Integer, CompletableFuture<byte[]>> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, Integer> nextIndex = new ConcurrentHashMap<>(); // следующий индекс для отправки нодам
    private final Map<String, Integer> matchIndex = new ConcurrentHashMap<>(); // последний подтвержденный индекс

    private final AtomicBoolean started = new AtomicBoolean(false); // флаг запуска узла

    private volatile RaftState state = RaftState.FOLLOWER; // текущий статус ноды
    private volatile long currentTerm = 0;
    private volatile String votedFor = null;
    private volatile String currentLeader = null;
    private volatile int commitIndex = 0;
    private volatile int lastApplied = 0;

    private ScheduledFuture<?> electionTask;
    private ScheduledFuture<?> heartbeatTask;

    public RaftNode(ClusterConfig config, RaftTransport transport, StateMachine stateMachine) {
        this.config = config;
        this.transport = transport;
        this.stateMachine = stateMachine;
        log.add(new LogEntry(0, 0, new byte[0]));
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("Node {} starting", config.getLocalId());
        resetElectionTimer();
    }

    public RaftState getState() {
        return state;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public String getCurrentLeader() {
        return currentLeader;
    }

    public String getLocalId() {
        return config.getLocalId();
    }

    // Подтверждение команды клиента
    public CompletableFuture<byte[]> submitCommand(byte[] command) {
        synchronized (this) {
            if (state != RaftState.LEADER) {
                throw new NotLeaderException(currentLeader);
            }
            int index = lastLogIndex() + 1;
            LogEntry entry = new LogEntry(currentTerm, index, command);
            log.add(entry);
            CompletableFuture<byte[]> result = new CompletableFuture<>();
            pendingResponses.put(index, result);
            broadcastReplications(); // асинхронная репликация команды на ноды/пиры
            return result;
        }
    }

    // Обработчик RPC репликации лога от лидера
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        synchronized (this) {
            LOGGER.info("[{}][HEARTBEAT] Node {} received AppendEntries from leader {}, term {}, entries.size={} (heartbeat if 0)", java.time.Instant.now(), config.getLocalId(), request.getLeaderId(), request.getTerm(), request.getEntries().size());
            if (request.getTerm() < currentTerm) {
                return new AppendEntriesResponse(currentTerm, false, lastLogIndex());
            }
            if (request.getTerm() > currentTerm) {
                becomeFollower(request.getTerm(), request.getLeaderId()); // переводим ноду в состояние FOLLOWER, обновляем терм и текущего лидера
            }
            else {
                currentLeader = request.getLeaderId(); // обновляем текущего лидера
            }
            if (state != RaftState.FOLLOWER) {
                becomeFollower(currentTerm, request.getLeaderId()); // переводим ноду в состояние FOLLOWER, обновляем терм и текущего лидера
            }
            resetElectionTimer(); // сбрасываем таймер выборов

            if (!logContains(request.getPrevLogIndex(), request.getPrevLogTerm())) {
                return new AppendEntriesResponse(currentTerm, false, lastLogIndex());
            }

            int index = request.getPrevLogIndex();
            for (LogEntry entry : request.getEntries()) {
                index++;
                // если запись уже есть и терм не совпадает — удаляем "хвост" и добавляем новую запись
                if (index < log.size()) {
                    LogEntry existing = log.get(index);
                    if (existing.getTerm() != entry.getTerm()) {
                        log.subList(index, log.size()).clear();
                        log.add(entry);
                    }
                } else {
                    log.add(entry); // добавляем запись в конец списка логов
                }
            }

            if (request.getLeaderCommit() > commitIndex) {
                commitIndex = Math.min(request.getLeaderCommit(), lastLogIndex()); // определяем общий подтвержденный/зафиксированный индекс
                applyCommittedEntries();
            }

            return new AppendEntriesResponse(currentTerm, true, lastLogIndex());
        }
    }

    // Обработчик голоса в голосовании
    public RequestVoteResponse handleRequestVote(RequestVoteRequest request) {
        synchronized (this) {
            if (request.getTerm() < currentTerm) {
                return new RequestVoteResponse(currentTerm, false);
            }
            if (request.getTerm() > currentTerm) {
                becomeFollower(request.getTerm(), null); // переводим ноду в состояние FOLLOWER, обновляем терм и текущего лидера
            }
            boolean voteGranted = false; // флаг отданного голоса
            boolean votedForCandidate = votedFor == null || votedFor.equals(request.getCandidateId()); // не голосовали или голосовали за этого кандидата
            boolean upToDate = isCandidateUpToDate(request.getLastLogIndex(), request.getLastLogTerm()); // проверка актуальности данных кандидата
            if (votedForCandidate && upToDate) {
                votedFor = request.getCandidateId();
                voteGranted = true;
                resetElectionTimer();
            }
            return new RequestVoteResponse(currentTerm, voteGranted);
        }
    }

    public byte[] readFromStateMachine(byte[] query) {
        return stateMachine.apply(query);
    }

    private void broadcastReplications() {
        LOGGER.info("[{}][HEARTBEAT] Leader {} broadcastReplications(), term {}", java.time.Instant.now(), config.getLocalId(), currentTerm);
        config.getPeers().keySet().forEach(peerId -> executor.submit(() -> replicatePeer(peerId)));
    }

    private void replicatePeer(String peerId) {
        // Подготовка запроса
        AppendEntriesRequest request;
        synchronized (this) {
            if (state != RaftState.LEADER) {
                return;
            }
            int next = nextIndex.computeIfAbsent(peerId, p -> lastLogIndex() + 1);
            int prevIndex = next - 1;
            long prevTerm = log.get(prevIndex).getTerm();
            List<LogEntry> entries = Collections.emptyList();
            if (next <= lastLogIndex()) {
                entries = new ArrayList<>(log.subList(next, log.size()));
            }
            request = new AppendEntriesRequest(currentTerm, config.getLocalId(), prevIndex, prevTerm, entries, commitIndex);
        }
        // Отправка запроса и обработка ответа
        transport.appendEntries(peerId, request).whenComplete((response, error) -> {
            if (error != null) {
                LOGGER.debug("AppendEntries to {} failed: {}", peerId, error.getMessage());
                return;
            }
            synchronized (this) {
                if (response.getTerm() > currentTerm) {
                    becomeFollower(response.getTerm(), null);
                    return;
                }
                if (state != RaftState.LEADER) {
                    return;
                }
                if (response.isSuccess()) {
                    int match = request.getPrevLogIndex() + request.getEntries().size(); // максимальный индекс, до которого пир точно имеет те же записи, что и лидер
                    matchIndex.put(peerId, match);
                    nextIndex.put(peerId, match + 1);
                    updateCommitIndex(); // на основе всех matchIndex лидер определяет, какие записи уже реплицированы большинством и соответственно сдвигает commitIndex
                } else { // пир не согласен с prevIndex/prevTerm или его лог не совпадает
                    int next = Math.max(1, nextIndex.getOrDefault(peerId, lastLogIndex() + 1) - 1); // откатываемся назад по nextIndex
                    nextIndex.put(peerId, next);
                    executor.submit(() -> replicatePeer(peerId)); // отправляем репликацию этому пиру снова
                }
            }
        });
    }

    private void updateCommitIndex() {
        int lastIdx = lastLogIndex();
        for (int i = lastIdx; i > commitIndex; i--) {
            int replicated = 1; // self
            for (Integer match : matchIndex.values()) {
                if (match >= i) {
                    replicated++;
                }
            }
            if (replicated >= config.majority() && log.get(i).getTerm() == currentTerm) {
                commitIndex = i;
                applyCommittedEntries();
                break;
            }
        }
    }

    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = log.get(lastApplied);
            byte[] result = stateMachine.apply(entry.getCommand());
            CompletableFuture<byte[]> future = pendingResponses.remove(entry.getIndex());
            if (future != null) {
                future.complete(result);
            }
        }
    }

    private void startElection() {
        RequestVoteRequest voteRequest;
        // Нода становится кандидатом и голосует за себя (подготовка запроса)
        synchronized (this) {
            resetElectionTimer();
            state = RaftState.CANDIDATE;
            currentTerm++;
            votedFor = config.getLocalId();
            currentLeader = null;
            int lastIndex = lastLogIndex();
            long lastTerm = log.get(lastIndex).getTerm();
            voteRequest = new RequestVoteRequest(currentTerm, config.getLocalId(), lastIndex, lastTerm); // запрос на голосование
        }

        AtomicInteger votes = new AtomicInteger(1); // счетчик голосов
        AtomicBoolean electionComplete = new AtomicBoolean(false); // флаг окончания голосования
        // Отправка запроса и обработка ответа
        config.getPeers().keySet().forEach(peerId -> transport.requestVote(peerId, voteRequest).whenComplete((response, error) -> {
            if (error != null) {
                LOGGER.debug("RequestVote to {} failed: {}", peerId, error.getMessage());
                return;
            }
            synchronized (this) {
                if (state != RaftState.CANDIDATE || response.getTerm() > currentTerm) {
                    if (response.getTerm() > currentTerm) {
                        becomeFollower(response.getTerm(), null); // меняем статус ноды на подписчика
                    }
                    return;
                }
                if (response.isVoteGranted()) {
                    int count = votes.incrementAndGet();
                    if (!electionComplete.get() && count >= config.majority()) {
                        electionComplete.set(true);
                        becomeLeader(); // меняем статус ноды на лидера
                    }
                }
            }
        }));
    }

    // Меняет роль ноды на LEADER, логирует смену роли,
    // инициализирует nextIndex/matchIndex для всех пиров и запускает периодический heartbeat
    private void becomeLeader() {
        LOGGER.info("[{}][ROLE] Node {} became LEADER term {}", java.time.Instant.now(), config.getLocalId(), currentTerm);
        state = RaftState.LEADER;
        currentLeader = config.getLocalId();
        nextIndex.clear();
        matchIndex.clear();
        int next = lastLogIndex() + 1;
        if (electionTask != null) {
            electionTask.cancel(false);
            electionTask = null;
        }
        config.getPeers().keySet().forEach(peer -> {
            nextIndex.put(peer, next);
            matchIndex.put(peer, 0);
        });
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(this::broadcastReplications,
                0,
                HEARTBEAT_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    // Меняет роль ноды на FOLLOWER, логирует смену роли, обновляет терм и текущего лидера,
    // отменяет heartbeat и перезапускает таймер выборов.
    private void becomeFollower(long term, String leaderId) {
        LOGGER.info("[{}][ROLE] Node {} became FOLLOWER term {}", java.time.Instant.now(), config.getLocalId(), term);
        state = RaftState.FOLLOWER;
        currentTerm = term;
        votedFor = null;
        currentLeader = leaderId;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        resetElectionTimer();
    }

    // Проверка, есть ли в логе запись с указанным индексом и термом
    private boolean logContains(int prevIndex, long prevTerm) {
        if (prevIndex >= log.size()) {
            return false;
        }
        return log.get(prevIndex).getTerm() == prevTerm;
    }

    // Проверка “свежести” лога кандидата с локальным логом
    private boolean isCandidateUpToDate(int candidateLastIndex, long candidateLastTerm) {
        int lastIndex = lastLogIndex();
        long lastTerm = log.get(lastIndex).getTerm();
        if (candidateLastTerm != lastTerm) {
            return candidateLastTerm > lastTerm;
        }
        return candidateLastIndex >= lastIndex;
    }

    private int lastLogIndex() {
        return log.size() - 1;
    }

    private void resetElectionTimer() {
        if (electionTask != null) {
            electionTask.cancel(false);
        }
        long timeout = MIN_ELECTION_TIMEOUT.toMillis() +
                random.nextInt((int) (MAX_ELECTION_TIMEOUT.toMillis() - MIN_ELECTION_TIMEOUT.toMillis()));
        LOGGER.info("[{}][TIMER] Node {} reset election timer to {} ms", java.time.Instant.now(), config.getLocalId(), timeout);
        electionTask = scheduler.schedule(this::startElection, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        executor.shutdownNow();
    }
}

