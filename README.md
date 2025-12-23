# Распределенное Key-Value хранилище с консенсусом на основе алгоритма Raft
## Описание системы
Распределенное отказоустойчивое Key-Value хранилище, требующее консенсуса на основе алгоритма Raft. Система способна к восстановлению без потери данных в случае гибели узлов. Язык разработки - Java.

### Основные возможности

### Алгоритм Raft

---

## Структура проекта

- `docker-compose.yml`, `Dockerfile` — контейнеризация и кластерный запуск  
- `pom.xml` — Maven-конфигурация

- `src/`
  - `main/`
    - `java/`
      - `org/`
        - `example/`
          - `kv/`
            - `KeyValueCommand.java` — команды для KV-хранилища (PUT, DELETE)
            - `KeyValueResult.java` — результат выполнения команд
            - `KeyValueStateMachine.java` — бизнес-логика state machine
          - `raft/`
            - `RaftNode.java` — основной узел Raft
            - `RaftState.java` — внутренние состояния узла
            - `StateMachine.java` — интерфейс state machine
            - `NotLeaderException.java` — ошибка для не-лидера
            - `log/`
              - `LogEntry.java` — лог записи Raft
            - `protocol/`
              - `AppendEntriesRequest.java`, `AppendEntriesResponse.java` — синхронизация лога
              - `RequestVoteRequest.java`, `RequestVoteResponse.java` — голосования при выборах
            - `cluster/`
              - `ClusterConfig.java` — конфигурация кластера
              - `PeerEndpoint.java` — адресация узлов
            - `transport/`
              - `RaftTransport.java` — абстракция транспорта
              - `HttpRaftTransport.java` — HTTP-транспорт
            - `util/`
              - `Json.java` — утилиты сериализации
          - `server/`
            - `RaftHttpServer.java` — HTTP-сервер кластера
          - `Main.java` — точка входа
    - `resources/` — ресурсы приложения
  - `test/`
    - `java/`
      - `org/`
        - `example/`
          - `kv/`
            - `KeyValueStateMachineTest.java` — тесты state machine

- `target/` — артефакты сборки (компилированные файлы)

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


