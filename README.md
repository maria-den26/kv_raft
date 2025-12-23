## KV Raft

Распределённое key-value хранилище с консенсусом на основе алгоритма Raft.

### Сборка

```bash
mvn clean package
```

### Запуск с Docker (рекомендуется)

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

### Запуск узлов вручную

Каждый узел поднимает HTTP сервер с RPC Raft и клиентскими endpoint'ами.

```bash
# узел 1
java -jar target/kv_raft-1.0-SNAPSHOT.jar \
  --id node1 --host 127.0.0.1 --port 9001 \
  --peers node2=127.0.0.1:9002,node3=127.0.0.1:9003,node4=127.0.0.1:9004

# узел 2
java -jar target/kv_raft-1.0-SNAPSHOT.jar \
  --id node2 --host 127.0.0.1 --port 9002 \
  --peers node1=127.0.0.1:9001,node3=127.0.0.1:9003,node4=127.0.0.1:9004

# узел 3
java -jar target/kv_raft-1.0-SNAPSHOT.jar \
  --id node3 --host 127.0.0.1 --port 9003 \
  --peers node1=127.0.0.1:9001,node2=127.0.0.1:9002,node4=127.0.0.1:9004

# узел 4
java -jar target/kv_raft-1.0-SNAPSHOT.jar \
  --id node4 --host 127.0.0.1 --port 9004 \
  --peers node1=127.0.0.1:9001,node2=127.0.0.1:9002,node3=127.0.0.1:9003
```

### Клиентский API

- `POST /kv/put` — тело `{ "key": "...", "value": "..." }`
- `POST /kv/delete` — тело `{ "key": "..." }`
- `GET /kv/get?key=...`

Обращаться следует к лидеру. Фолловер вернёт HTTP 409 с подсказкой `leader`.


