# Распределенное Key-Value хранилище с консенсусом на основе алгоритма Raft
## Описание системы
Распределенное отказоустойчивое Key-Value хранилище, требующее консенсуса на основе алгоритма Raft. Система способна к восстановлению без потери данных в случае гибели узлов. Язык разработки - Java.

### Основные возможности

### Алгоритм Raft

## Структура проекта

- `docker-compose.yml`, `Dockerfile` — конфигурация для запуска проекта в контейнере и/или кластере.
- `pom.xml` — Maven-конфигурация для сборки, зависимостей и тестов.

### src/main/java/org/example:
<details>
<summary><b>kv/</b></summary>

- `KeyValueCommand.java` — команды для управления хранилищем (например, PUT, DELETE).
- `KeyValueResult.java` — результат выполнения команд.
- `KeyValueStateMachine.java` — реализация прикладной state machine для хранения и обработки команд key-value.

</details>

<details>
<summary><b>raft/</b></summary>

- `RaftNode.java` — основной класс-узел Raft, реализующий протокол.
- `RaftState.java` — внутреннее состояние узла Raft.
- `StateMachine.java` — абстракция для конечных автоматов.

  - **log/**
    - `LogEntry.java` — лог записей протокола Raft.
  - **protocol/**
    - `AppendEntriesRequest/Response.java`, `RequestVoteRequest/Response.java` — сетевые сообщения протокола Raft.
  - **cluster/**
    - `ClusterConfig.java`, `PeerEndpoint.java` — определение конфигураций и адресов узлов кластера.
  - **transport/**
    - `RaftTransport.java` — абстракция транспортного уровня (между узлами).
    - `HttpRaftTransport.java` — реализация транспорта через HTTP.
  - **util/**
    - `Json.java` — вспомогательные методы для (де-)сериализации.
  - `NotLeaderException.java` — исключение при ошибках лидерства.

</details>

<details>
<summary><b>server/</b></summary>

- `RaftHttpServer.java` — HTTP сервер для доступа к кластеру, хранилищу и управления.

</details>

- `Main.java` — точка входа в приложение.

### src/test/java/org/example:
<details>
<summary><b>kv/</b></summary>

- `KeyValueStateMachineTest.java` — тестирование работы state machine key-value.

</details>

---

### Запуск с Docker

Все узлы кластера упакованы в отдельные Docker контейнеры.

```bash
# Сборка образов и запуск кластера
docker-compose up --build

# Запуск в фоновом режиме
docker-compose up -d --build

# Остановка кластера
docker-compose down

# Просмотр логов
docker-compose logs -f

# Просмотр логов конкретного узла
docker-compose logs -f node1
```

Узлы будут доступны на портах:
- `node1`: http://localhost:9001
- `node2`: http://localhost:9002
- `node3`: http://localhost:9003
- `node4`: http://localhost:9004

```

### API

- `POST /kv/put` — тело `{ "key": "...", "value": "..." }` // запись пары ключ-значение
- `POST /kv/delete` — тело `{ "key": "..." }` // удаление значения по ключу
- `GET /kv/get?key=...` // чтение значения по ключу

Обращаться следует к лидеру. Фолловер вернёт HTTP 409 с подсказкой `leader`.


