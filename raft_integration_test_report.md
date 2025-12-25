# Отчет интеграционного тестирования Raft кластера

**Дата и время выполнения:** 2025-12-25 17:06:16

## Сводка результатов


| Статус | Количество |
|--------|------------|
| PASS | 6 |
| FAIL | 3 |
| SKIP | 0 |
| **Всего** | **9** |


## Детальные результаты тестов

### [PASS] startup_and_leader_election (11.46s)

Проверка запуска кластера и выбора лидера

**Логи выполнения:**

```
[17:01:40] Starting test execution
[17:01:40] Starting cluster...
[17:01:51] Cluster started, waiting for leader election...
[17:01:52] SUCCESS: Leader elected - node3
[17:01:52] Node node1: role=FOLLOWER, term=2
[17:01:52] Node node2: role=FOLLOWER, term=2
[17:01:52] Node node3: role=LEADER, term=2
[17:01:52] Node node4: role=FOLLOWER, term=2
[17:01:52] Test finished with result: PASSED (duration: 11.46s)
```

### [PASS] data_replication (5.31s)

Проверка репликации данных лидера на фолловеров

**Логи выполнения:**

```
[17:01:52] Starting test execution
[17:01:52] Current leader: node3
[17:01:52] SUCCESS: Put test_key_1=test_value_1
[17:01:52] SUCCESS: Put test_key_2=test_value_2
[17:01:52] SUCCESS: Put test_key_3=test_value_3
[17:01:57] SUCCESS: Node node1 has correct value for test_key_1
[17:01:57] SUCCESS: Node node2 has correct value for test_key_1
[17:01:57] SUCCESS: Node node3 has correct value for test_key_1
[17:01:57] SUCCESS: Node node4 has correct value for test_key_1
[17:01:57] SUCCESS: Node node1 has correct value for test_key_2
[17:01:57] SUCCESS: Node node2 has correct value for test_key_2
[17:01:57] SUCCESS: Node node3 has correct value for test_key_2
[17:01:57] SUCCESS: Node node4 has correct value for test_key_2
[17:01:57] SUCCESS: Node node1 has correct value for test_key_3
[17:01:57] SUCCESS: Node node2 has correct value for test_key_3
[17:01:57] SUCCESS: Node node3 has correct value for test_key_3
[17:01:57] SUCCESS: Node node4 has correct value for test_key_3
[17:01:57] Test finished with result: PASSED (duration: 5.31s)
```

### [FAIL] majority_survival (16.99s)

Проверка работоспособности при отказе 1 узла из 4

**Логи выполнения:**

```
[17:01:57] Starting test execution
[17:01:57] Initial leader: node3
[17:01:57] Stopping node: node4
[17:02:09] New leader after failure: node3
[17:02:14] ERROR: Failed to put data after node failure: HTTP 500: error:null
[17:02:14] Test finished with result: FAILED (duration: 16.99s)
```

### [PASS] edge_case_no_quorum (60.51s)

Проверка отказа кластера при отсутствии кворума (2 из 4 узлов)

**Логи выполнения:**

```
[17:02:14] Starting test execution
[17:02:14] Stopping node: node3
[17:02:19] Stopping node: node4
[17:02:45] SUCCESS: No leader elected (no quorum as expected)
[17:02:53] SUCCESS: Write operations correctly failed without quorum
[17:02:53] Restarting node: node3
[17:02:59] Restarting node: node4
[17:03:14] Test finished with result: PASSED (duration: 60.51s)
```

### [PASS] insufficient_quorum (53.65s)

Проверка отказа кластера при доступности менее половины узлов (1 из 4)

**Логи выполнения:**

```
[17:03:14] Starting test execution
[17:03:14] Stopping node: node2
[17:03:17] Stopping node: node3
[17:03:20] Stopping node: node4
[17:03:41] SUCCESS: No leader elected (insufficient quorum as expected)
[17:03:41] Restarting node: node2
[17:03:47] Restarting node: node3
[17:03:52] Restarting node: node4
[17:04:08] Test finished with result: PASSED (duration: 53.65s)
```

### [PASS] leader_failure (17.47s)

Проверка перевыбора лидера при его недоступности

**Логи выполнения:**

```
[17:04:08] Starting test execution
[17:04:08] Initial leader: node2
[17:04:08] Stopping current leader: node2
[17:04:20] SUCCESS: New leader elected: node1
[17:04:20] Restarting old leader: node2
[17:04:25] Test finished with result: PASSED (duration: 17.47s)
```

### [PASS] leader_return (27.75s)

Проверка синхронизации вернувшегося лидера с текущим состоянием кластера

**Логи выполнения:**

```
[17:04:25] Starting test execution
[17:04:26] Current leader: node4
[17:04:26] Stopping current leader: node4
[17:04:38] New leader: node3
[17:04:38] Restarting old leader: node4
[17:04:53] SUCCESS: Leader node4 successfully synchronized with cluster state
[17:04:53] Test finished with result: PASSED (duration: 27.75s)
```

### [FAIL] follower_return (7.81s)

Проверка синхронизации вернувшейся ноды follower с текущим состоянием кластера

**Логи выполнения:**

```
[17:04:53] Starting test execution
[17:04:53] Current leader: node3
[17:04:53] Stopping follower: node4
[17:05:01] ERROR: Failed to put data while follower is down: HTTP 500: error:null
[17:05:01] Test finished with result: FAILED (duration: 7.81s)
```

### [FAIL] data_persistence (72.75s)

Проверка целостности и доступности данных после смены лидера

**Логи выполнения:**

```
[17:05:01] Starting test execution
[17:05:05] Initial leader: node1
[17:05:05] SUCCESS: Stored 10 key-value pairs
[17:05:05] Forcing leader change by stopping node1
[17:06:14] ERROR: No new leader elected
[17:06:14] Test finished with result: FAILED (duration: 72.75s)
```

