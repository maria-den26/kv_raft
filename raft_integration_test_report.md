# Отчет интеграционного тестирования Raft кластера

**Дата и время выполнения:** 2025-12-25 16:45:31

## Сводка результатов


| Статус | Количество |
|--------|------------|
| PASS | 4 |
| FAIL | 5 |
| SKIP | 0 |
| **Всего** | **9** |


## Детальные результаты тестов

### [PASS] startup_and_leader_election (49.36s)

Проверка запуска кластера и выбора лидера

**Логи выполнения:**

```
[16:40:58] Starting test execution
[16:40:58] Starting cluster...
[16:41:47] Cluster started, waiting for leader election...
[16:41:47] SUCCESS: Leader elected - node1
[16:41:47] Node node1: role=LEADER, term=2
[16:41:47] Node node2: role=FOLLOWER, term=2
[16:41:47] Node node3: role=FOLLOWER, term=2
[16:41:47] Node node4: role=FOLLOWER, term=2
[16:41:47] Test finished with result: PASSED (duration: 49.36s)
```

### [PASS] data_replication (5.22s)

Проверка репликации данных лидера на фолловеров

**Логи выполнения:**

```
[16:41:47] Starting test execution
[16:41:47] Current leader: node1
[16:41:47] SUCCESS: Put test_key_1=test_value_1
[16:41:47] SUCCESS: Put test_key_2=test_value_2
[16:41:47] SUCCESS: Put test_key_3=test_value_3
[16:41:52] SUCCESS: Node node1 has correct value for test_key_1
[16:41:52] SUCCESS: Node node2 has correct value for test_key_1
[16:41:52] SUCCESS: Node node3 has correct value for test_key_1
[16:41:52] SUCCESS: Node node4 has correct value for test_key_1
[16:41:52] SUCCESS: Node node1 has correct value for test_key_2
[16:41:52] SUCCESS: Node node2 has correct value for test_key_2
[16:41:52] SUCCESS: Node node3 has correct value for test_key_2
[16:41:52] SUCCESS: Node node4 has correct value for test_key_2
[16:41:52] SUCCESS: Node node1 has correct value for test_key_3
[16:41:52] SUCCESS: Node node2 has correct value for test_key_3
[16:41:52] SUCCESS: Node node3 has correct value for test_key_3
[16:41:52] SUCCESS: Node node4 has correct value for test_key_3
[16:41:52] Test finished with result: PASSED (duration: 5.22s)
```

### [FAIL] majority_survival (17.12s)

Проверка работоспособности при отказе 1 узла из 4

**Логи выполнения:**

```
[16:41:52] Starting test execution
[16:41:52] Initial leader: node1
[16:41:52] Stopping node: node4
[16:42:04] New leader after failure: node3
[16:42:09] ERROR: Failed to put data after node failure: HTTP 500: error:null
[16:42:09] Test finished with result: FAILED (duration: 17.12s)
```

### [PASS] edge_case_no_quorum (60.07s)

Проверка отказа кластера при отсутствии кворума (2 из 4 узлов)

**Логи выполнения:**

```
[16:42:09] Starting test execution
[16:42:09] Stopping node: node3
[16:42:15] Stopping node: node4
[16:42:40] SUCCESS: No leader elected (no quorum as expected)
[16:42:48] SUCCESS: Write operations correctly failed without quorum
[16:42:48] Restarting node: node3
[16:42:54] Restarting node: node4
[16:43:09] Test finished with result: PASSED (duration: 60.07s)
```

### [PASS] insufficient_quorum (53.59s)

Проверка отказа кластера при доступности менее половины узлов (1 из 4)

**Логи выполнения:**

```
[16:43:09] Starting test execution
[16:43:09] Stopping node: node2
[16:43:12] Stopping node: node3
[16:43:15] Stopping node: node4
[16:43:36] SUCCESS: No leader elected (insufficient quorum as expected)
[16:43:36] Restarting node: node2
[16:43:42] Restarting node: node3
[16:43:47] Restarting node: node4
[16:44:03] Test finished with result: PASSED (duration: 53.59s)
```

### [FAIL] leader_failure (16.18s)

Проверка перевыбора лидера при его недоступности

**Логи выполнения:**

```
[16:44:03] Starting test execution
[16:44:03] Initial leader: node2
[16:44:03] Stopping current leader: node2
[16:44:15] SUCCESS: New leader elected: node4
[16:44:19] ERROR: Failed to put data with new leader: No leader found
[16:44:19] Test finished with result: FAILED (duration: 16.18s)
```

### [FAIL] leader_return (30.31s)

Проверка синхронизации вернувшегося лидера с текущим состоянием кластера

**Логи выполнения:**

```
[16:44:19] Starting test execution
[16:44:23] Current leader: node1
[16:44:23] Stopping current leader: node1
[16:44:49] ERROR: Invalid new leader after stopping current leader
[16:44:49] Test finished with result: FAILED (duration: 30.31s)
```

### [FAIL] follower_return (18.45s)

Проверка синхронизации вернувшейся ноды follower с текущим состоянием кластера

**Логи выполнения:**

```
[16:44:49] Starting test execution
[16:45:08] ERROR: No leader found
[16:45:08] Test finished with result: FAILED (duration: 18.45s)
```

### [FAIL] data_persistence (18.49s)

Проверка целостности и доступности данных после смены лидера

**Логи выполнения:**

```
[16:45:08] Starting test execution
[16:45:26] ERROR: No initial leader found
[16:45:26] Test finished with result: FAILED (duration: 18.49s)
```

