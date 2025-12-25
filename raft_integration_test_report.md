# Отчет интеграционного тестирования Raft кластера

**Дата и время выполнения:** 2025-12-25 21:58:31

## Сводка результатов


| Статус | Количество |
|--------|------------|
| PASS | 9 |
| FAIL | 0 |
| SKIP | 0 |
| **Всего** | **9** |


## Детальные результаты тестов

### [PASS] startup_and_leader_election (11.53s)

Проверка запуска кластера и выбора лидера

**Логи выполнения:**

```
[21:52:56] Starting test execution
[21:52:56] Starting cluster...
[21:53:07] Cluster started, waiting for leader election...
[21:53:08] SUCCESS: Leader elected - node2
[21:53:08] Node node1: role=FOLLOWER, term=2
[21:53:08] Node node2: role=LEADER, term=2
[21:53:08] Node node3: role=FOLLOWER, term=2
[21:53:08] Node node4: role=FOLLOWER, term=2
[21:53:08] Test finished with result: PASSED (duration: 11.53s)
```

### [PASS] data_replication (5.24s)

Проверка репликации данных лидера на фолловеров

**Логи выполнения:**

```
[21:53:08] Starting test execution
[21:53:08] Current leader: node2
[21:53:08] SUCCESS: Put test_key_1=test_value_1
[21:53:08] SUCCESS: Put test_key_2=test_value_2
[21:53:08] SUCCESS: Put test_key_3=test_value_3
[21:53:13] SUCCESS: Node node1 has correct value for test_key_1
[21:53:13] SUCCESS: Node node2 has correct value for test_key_1
[21:53:13] SUCCESS: Node node3 has correct value for test_key_1
[21:53:13] SUCCESS: Node node4 has correct value for test_key_1
[21:53:13] SUCCESS: Node node1 has correct value for test_key_2
[21:53:13] SUCCESS: Node node2 has correct value for test_key_2
[21:53:13] SUCCESS: Node node3 has correct value for test_key_2
[21:53:13] SUCCESS: Node node4 has correct value for test_key_2
[21:53:13] SUCCESS: Node node1 has correct value for test_key_3
[21:53:13] SUCCESS: Node node2 has correct value for test_key_3
[21:53:13] SUCCESS: Node node3 has correct value for test_key_3
[21:53:13] SUCCESS: Node node4 has correct value for test_key_3
[21:53:13] Test finished with result: PASSED (duration: 5.24s)
```

### [PASS] majority_survival (51.79s)

Проверка работоспособности при отказе 1 узла из 4

**Логи выполнения:**

```
[21:53:13] Starting test execution
[21:53:13] Initial leader: node2
[21:53:13] Stopping node: node4
[21:53:29] New leader after failure: node1
[21:53:59] Restarting node: node4
[21:54:05] Test finished with result: PASSED (duration: 51.79s)
```

### [PASS] edge_case_no_quorum (66.85s)

Проверка отказа кластера при отсутствии кворума (2 из 4 узлов)

**Логи выполнения:**

```
[21:54:05] Starting test execution
[21:54:05] Stopping node: node3
[21:54:12] Stopping node: node4
[21:54:42] SUCCESS: No leader elected (no quorum as expected)
[21:54:51] SUCCESS: Write operations correctly failed without quorum
[21:54:51] Restarting node: node3
[21:54:56] Restarting node: node4
[21:55:12] Test finished with result: PASSED (duration: 66.85s)
```

### [PASS] insufficient_quorum (65.77s)

Проверка отказа кластера при доступности менее половины узлов (1 из 4)

**Логи выполнения:**

```
[21:55:12] Starting test execution
[21:55:12] Stopping node: node2
[21:55:18] Stopping node: node3
[21:55:26] Stopping node: node4
[21:55:51] SUCCESS: No leader elected (insufficient quorum as expected)
[21:55:51] Restarting node: node2
[21:55:56] Restarting node: node3
[21:56:02] Restarting node: node4
[21:56:17] Test finished with result: PASSED (duration: 65.77s)
```

### [PASS] leader_failure (26.85s)

Проверка перевыбора лидера при его недоступности

**Логи выполнения:**

```
[21:56:17] Starting test execution
[21:56:17] Initial leader: node2
[21:56:17] Stopping current leader: node2
[21:56:39] SUCCESS: New leader elected: node1
[21:56:39] Restarting old leader: node2
[21:56:44] Test finished with result: PASSED (duration: 26.85s)
```

### [PASS] leader_return (35.80s)

Проверка синхронизации вернувшегося лидера с текущим состоянием кластера

**Логи выполнения:**

```
[21:56:44] Starting test execution
[21:56:44] Current leader: node1
[21:56:44] Stopping current leader: node1
[21:57:00] New leader: node4
[21:57:04] Restarting old leader: node1
[21:57:20] SUCCESS: Leader node1 successfully synchronized with cluster state
[21:57:20] Test finished with result: PASSED (duration: 35.80s)
```

### [PASS] follower_return (42.64s)

Проверка синхронизации вернувшейся ноды follower с текущим состоянием кластера

**Логи выполнения:**

```
[21:57:20] Starting test execution
[21:57:20] Current leader: node2
[21:57:20] Stopping follower: node4
[21:57:42] Restarting follower: node4
[21:58:03] SUCCESS: Follower node4 successfully synchronized with cluster state
[21:58:03] Test finished with result: PASSED (duration: 42.64s)
```

### [PASS] data_persistence (26.36s)

Проверка целостности и доступности данных после смены лидера

**Логи выполнения:**

```
[21:58:03] Starting test execution
[21:58:03] Initial leader: node2
[21:58:03] SUCCESS: Stored 10 key-value pairs
[21:58:03] Forcing leader change by stopping node2
[21:58:25] New leader: node3
[21:58:25] SUCCESS: persistence_key_0 preserved correctly
[21:58:25] SUCCESS: persistence_key_1 preserved correctly
[21:58:25] SUCCESS: persistence_key_2 preserved correctly
[21:58:25] SUCCESS: persistence_key_3 preserved correctly
[21:58:25] SUCCESS: persistence_key_4 preserved correctly
[21:58:25] SUCCESS: persistence_key_5 preserved correctly
[21:58:25] SUCCESS: persistence_key_6 preserved correctly
[21:58:25] SUCCESS: persistence_key_7 preserved correctly
[21:58:25] SUCCESS: persistence_key_8 preserved correctly
[21:58:25] SUCCESS: persistence_key_9 preserved correctly
[21:58:29] SUCCESS: All data persisted correctly through leader change
[21:58:29] Test finished with result: PASSED (duration: 26.36s)
```

