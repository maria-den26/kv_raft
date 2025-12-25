#!/usr/bin/env python3
"""
Интеграционные тесты для Raft кластера.
Тестирует различные сценарии отказоустойчивости и корректности работы.
"""

import subprocess
import time
import requests
import logging
from datetime import datetime
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
import json


class TestResult(Enum):
    PASSED = "PASSED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"


@dataclass
class TestCase:
    name: str
    description: str
    result: TestResult = TestResult.SKIPPED
    logs: List[str] = None
    start_time: Optional[float] = None
    end_time: Optional[float] = None

    def __post_init__(self):
        if self.logs is None:
            self.logs = []

    def log(self, message: str):
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.logs.append(f"[{timestamp}] {message}")
        print(f"[{self.name}] {message}")

    def start(self):
        self.start_time = time.time()
        self.log("Starting test execution")

    def finish(self, result: TestResult):
        self.end_time = time.time()
        self.result = result
        duration = self.end_time - self.start_time if self.start_time else 0
        self.log(f"Test finished with result: {result.value} (duration: {duration:.2f}s)")


class RaftClusterManager:
    """Менеджер кластера Raft для интеграционных тестов."""

    def __init__(self, compose_file: str = "docker-compose.yml"):
        self.compose_file = compose_file
        self.nodes = ["node1", "node2", "node3", "node4"]
        self.ports = {"node1": 9001, "node2": 9002, "node3": 9003, "node4": 9004}

    def start_cluster(self) -> bool:
        """Запускает весь кластер."""
        try:
            subprocess.run(
                ["docker-compose", "-f", self.compose_file, "up", "-d"],
                check=True,
                capture_output=True
            )
            time.sleep(10)  # Ждем запуска кластера
            return True
        except subprocess.CalledProcessError as e:
            logging.error(f"Failed to start cluster: {e}")
            return False

    def stop_cluster(self) -> bool:
        """Останавливает весь кластер."""
        try:
            subprocess.run(
                ["docker-compose", "-f", self.compose_file, "down"],
                check=True,
                capture_output=True
            )
            return True
        except subprocess.CalledProcessError as e:
            logging.error(f"Failed to stop cluster: {e}")
            return False

    def stop_node(self, node: str) -> bool:
        """Останавливает конкретный узел."""
        try:
            subprocess.run(
                ["docker-compose", "-f", self.compose_file, "stop", node],
                check=True,
                capture_output=True
            )
            time.sleep(2)  # Ждем остановки
            return True
        except subprocess.CalledProcessError as e:
            logging.error(f"Failed to stop node {node}: {e}")
            return False

    def start_node(self, node: str) -> bool:
        """Запускает конкретный узел."""
        try:
            subprocess.run(
                ["docker-compose", "-f", self.compose_file, "start", node],
                check=True,
                capture_output=True
            )
            time.sleep(5)  # Ждем запуска узла
            return True
        except subprocess.CalledProcessError as e:
            logging.error(f"Failed to start node {node}: {e}")
            return False

    def get_node_status(self, node: str) -> Optional[Dict]:
        """Получает статус узла."""
        try:
            port = self.ports[node]
            response = requests.get(f"http://localhost:{port}/raft/status", timeout=5)
            if response.status_code == 200:
                return response.json()
        except requests.RequestException:
            pass
        return None

    def wait_for_leader_election(self, timeout: int = 30) -> Optional[str]:
        """Ждет выбора лидера в кластере."""
        start_time = time.time()
        while time.time() - start_time < timeout:
            leaders = []
            for node in self.nodes:
                status = self.get_node_status(node)
                if status and status.get("role") == "LEADER":
                    leaders.append(status.get("id"))

            if len(leaders) == 1:
                return leaders[0]

            time.sleep(1)
        return None

    def wait_for_node_unavailable(self, node: str, timeout: int = 10) -> bool:
        """Ждет пока узел станет недоступным."""
        start_time = time.time()
        while time.time() - start_time < timeout:
            if self.get_node_status(node) is None:
                return True
            time.sleep(1)
        return False


class RaftAPIClient:
    """Клиент для работы с Raft API."""

    def __init__(self, base_urls: List[str]):
        self.base_urls = base_urls
        self.session = requests.Session()

    def _find_leader(self) -> Optional[str]:
        """Находит URL лидера."""
        for url in self.base_urls:
            try:
                response = self.session.get(f"{url}/raft/status", timeout=5)
                if response.status_code == 200:
                    status = response.json()
                    if status.get("role") == "LEADER":
                        return url
            except requests.RequestException:
                continue
        return None

    def put(self, key: str, value: str) -> Tuple[bool, str]:
        """Записывает значение по ключу."""
        leader_url = self._find_leader()
        if not leader_url:
            return False, "No leader found"

        try:
            data = {"type": "PUT", "key": key, "value": value}
            response = self.session.post(
                f"{leader_url}/kv/put",
                json=data,
                timeout=10
            )

            if response.status_code == 200:
                result = response.json()
                return result.get("success", False), result.get("message", "")
            elif response.status_code == 409:
                # Redirect to leader
                leader_hint = response.json().get("leader")
                if leader_hint:
                    return False, f"Redirected to leader: {leader_hint}"
                return False, "Not leader response"
            else:
                return False, f"HTTP {response.status_code}: {response.text}"
        except requests.RequestException as e:
            return False, f"Request failed: {str(e)}"

    def get(self, key: str) -> Tuple[bool, Optional[str]]:
        """Читает значение по ключу."""
        for url in self.base_urls:
            try:
                response = self.session.get(f"{url}/kv/get?key={key}", timeout=5)
                if response.status_code == 200:
                    result = response.json()
                    if result.get("success"):
                        return True, result.get("value")
                    else:
                        return False, result.get("message")
                elif response.status_code == 500:
                    continue  # Try next node
            except requests.RequestException:
                continue
        return False, "All nodes unavailable"

    def delete(self, key: str) -> Tuple[bool, str]:
        """Удаляет значение по ключу."""
        leader_url = self._find_leader()
        if not leader_url:
            return False, "No leader found"

        try:
            data = {"type": "DELETE", "key": key, "value": None}
            response = self.session.post(
                f"{leader_url}/kv/delete",
                json=data,
                timeout=10
            )

            if response.status_code == 200:
                result = response.json()
                return result.get("success", False), result.get("message", "")
            elif response.status_code == 409:
                return False, "Not leader response"
            else:
                return False, f"HTTP {response.status_code}: {response.text}"
        except requests.RequestException as e:
            return False, f"Request failed: {str(e)}"


class RaftIntegrationTest:
    """Основной класс для интеграционных тестов Raft."""

    def __init__(self):
        self.cluster = RaftClusterManager()
        self.api_client = RaftAPIClient([
            "http://localhost:9001",
            "http://localhost:9002",
            "http://localhost:9003",
            "http://localhost:9004"
        ])
        self.test_cases: List[TestCase] = []

    def add_test_case(self, name: str, description: str) -> TestCase:
        """Добавляет тестовый случай."""
        test_case = TestCase(name=name, description=description)
        self.test_cases.append(test_case)
        return test_case

    def run_test_startup_and_leader_election(self) -> TestResult:
        """Тест 1: Начало работы - проверка запуска кластера и выбора лидера."""
        test = self.add_test_case(
            "startup_and_leader_election",
            "Проверка запуска кластера и выбора лидера"
        )
        test.start()

        try:
            # Запускаем кластер
            test.log("Starting cluster...")
            if not self.cluster.start_cluster():
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log("Cluster started, waiting for leader election...")

            # Ждем выбора лидера
            leader = self.cluster.wait_for_leader_election(30)
            if not leader:
                test.log("ERROR: Leader election failed - no leader elected within timeout")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"SUCCESS: Leader elected - {leader}")

            # Проверяем статус всех узлов
            for node in self.cluster.nodes:
                status = self.cluster.get_node_status(node)
                if status:
                    test.log(f"Node {node}: role={status.get('role')}, term={status.get('term')}")
                else:
                    test.log(f"WARNING: Node {node} is not responding")

            test.finish(TestResult.PASSED)
            return TestResult.PASSED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_data_replication(self) -> TestResult:
        """Тест 2: Репликация - проверка репликации данных лидера на фолловеров."""
        test = self.add_test_case(
            "data_replication",
            "Проверка репликации данных лидера на фолловеров"
        )
        test.start()

        try:
            # Проверяем, что кластер запущен и есть лидер
            leader = self.cluster.wait_for_leader_election(10)
            if not leader:
                test.log("ERROR: No leader found")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"Current leader: {leader}")

            # Записываем тестовые данные
            test_data = [
                ("test_key_1", "test_value_1"),
                ("test_key_2", "test_value_2"),
                ("test_key_3", "test_value_3")
            ]

            for key, value in test_data:
                success, message = self.api_client.put(key, value)
                if success:
                    test.log(f"SUCCESS: Put {key}={value}")
                else:
                    test.log(f"ERROR: Failed to put {key}={value}: {message}")
                    test.finish(TestResult.FAILED)
                    return TestResult.FAILED

            # Ждем репликации
            time.sleep(5)

            # Проверяем данные на всех узлах
            all_nodes_consistent = True
            for key, expected_value in test_data:
                for node in self.cluster.nodes:
                    success, value = self.api_client.get(key)
                    if success and value == expected_value:
                        test.log(f"SUCCESS: Node {node} has correct value for {key}")
                    else:
                        test.log(f"ERROR: Node {node} has incorrect value for {key}: expected '{expected_value}', got '{value}'")
                        all_nodes_consistent = False

            if all_nodes_consistent:
                test.finish(TestResult.PASSED)
                return TestResult.PASSED
            else:
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_majority_survival(self) -> TestResult:
        """Тест 3: Выживание большинства - проверка работоспособности, если отказал 1 узел из 4."""
        test = self.add_test_case(
            "majority_survival",
            "Проверка работоспособности при отказе 1 узла из 4"
        )
        test.start()

        try:
            # Проверяем начальное состояние
            leader = self.cluster.wait_for_leader_election(10)
            if not leader:
                test.log("ERROR: No initial leader found")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"Initial leader: {leader}")

            # Записываем тестовые данные
            success, message = self.api_client.put("survival_key", "survival_value")
            if not success:
                test.log(f"ERROR: Failed to put initial data: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Выбираем узел для остановки (не лидер, если возможно)
            node_to_stop = "node4" if leader != "node4" else "node3"
            test.log(f"Stopping node: {node_to_stop}")

            if not self.cluster.stop_node(node_to_stop):
                test.log(f"ERROR: Failed to stop node {node_to_stop}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Ждем пока узел станет недоступным
            if not self.cluster.wait_for_node_unavailable(node_to_stop, 10):
                test.log(f"WARNING: Node {node_to_stop} is still responding after stop")

            # Ждем стабилизации кластера
            time.sleep(5)

            # Проверяем, что кластер все еще работает
            new_leader = self.cluster.wait_for_leader_election(15)
            if not new_leader:
                test.log("ERROR: No leader after node failure")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"New leader after failure: {new_leader}")

            # Ждем стабилизации кластера
            time.sleep(30)

            # Проверяем, что можем записывать новые данные
            success, message = self.api_client.put("survival_key2", "survival_value2")
            if not success:
                test.log(f"ERROR: Failed to put data after node failure: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Проверяем, что старые данные все еще доступны
            success, value = self.api_client.get("survival_key")
            if not (success and value == "survival_value"):
                test.log(f"ERROR: Old data lost or corrupted: expected 'survival_value', got '{value}'")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Возвращаем узел
            test.log(f"Restarting node: {node_to_stop}")
            if not self.cluster.start_node(node_to_stop):
                test.log(f"WARNING: Failed to restart node {node_to_stop}")

            test.finish(TestResult.PASSED)
            return TestResult.PASSED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_edge_case_no_quorum(self) -> TestResult:
        """Тест 4: Граничный случай - проверка 'отказа' кластера, если отказали 2 узла из 2 (отсутствие кворума)."""
        test = self.add_test_case(
            "edge_case_no_quorum",
            "Проверка отказа кластера при отсутствии кворума (2 из 4 узлов)"
        )
        test.start()

        try:
            # Останавливаем 2 узла, оставляя только 2
            nodes_to_stop = ["node3", "node4"]
            for node in nodes_to_stop:
                test.log(f"Stopping node: {node}")
                if not self.cluster.stop_node(node):
                    test.log(f"ERROR: Failed to stop node {node}")
                    test.finish(TestResult.FAILED)
                    return TestResult.FAILED

                # Ждем пока узел станет недоступным
                if not self.cluster.wait_for_node_unavailable(node, 10):
                    test.log(f"WARNING: Node {node} is still responding after stop")

            # Ждем стабилизации кластера
            time.sleep(5)

            # Проверяем, что нет лидера (отсутствие кворума)
            leader = self.cluster.wait_for_leader_election(10)
            if leader:
                test.log(f"ERROR: Unexpected leader found: {leader} (should have no quorum)")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log("SUCCESS: No leader elected (no quorum as expected)")

            # Проверяем, что операции записи недоступны
            success, message = self.api_client.put("no_quorum_key", "no_quorum_value")
            if success:
                test.log("ERROR: Write operation succeeded when it should have failed")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log("SUCCESS: Write operations correctly failed without quorum")

            # Возвращаем узлы
            for node in nodes_to_stop:
                test.log(f"Restarting node: {node}")
                if not self.cluster.start_node(node):
                    test.log(f"WARNING: Failed to restart node {node}")

            # Ждем восстановления кластера
            time.sleep(10)
            leader = self.cluster.wait_for_leader_election(20)
            if not leader:
                test.log("WARNING: Cluster did not recover after restarting nodes")

            test.finish(TestResult.PASSED)
            return TestResult.PASSED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_insufficient_quorum(self) -> TestResult:
        """Тест 5: Отсутствие кворума - проверка 'отказа' кластера, если доступно меньше половины узлов (1 из 4)."""
        test = self.add_test_case(
            "insufficient_quorum",
            "Проверка отказа кластера при доступности менее половины узлов (1 из 4)"
        )
        test.start()

        try:
            # Останавливаем 3 узла, оставляя только 1
            nodes_to_stop = ["node2", "node3", "node4"]
            for node in nodes_to_stop:
                test.log(f"Stopping node: {node}")
                if not self.cluster.stop_node(node):
                    test.log(f"ERROR: Failed to stop node {node}")
                    test.finish(TestResult.FAILED)
                    return TestResult.FAILED

                # Ждем пока узел станет недоступным
                if not self.cluster.wait_for_node_unavailable(node, 10):
                    test.log(f"WARNING: Node {node} is still responding after stop")

            # Ждем стабилизации кластера
            time.sleep(5)

            # Проверяем, что нет лидера (отсутствие кворума)
            leader = self.cluster.wait_for_leader_election(10)
            if leader:
                test.log(f"ERROR: Unexpected leader found: {leader} (insufficient quorum)")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log("SUCCESS: No leader elected (insufficient quorum as expected)")

            # Возвращаем узлы
            for node in nodes_to_stop:
                test.log(f"Restarting node: {node}")
                if not self.cluster.start_node(node):
                    test.log(f"WARNING: Failed to restart node {node}")

            # Ждем восстановления кластера
            time.sleep(10)
            leader = self.cluster.wait_for_leader_election(20)
            if not leader:
                test.log("WARNING: Cluster did not recover after restarting nodes")

            test.finish(TestResult.PASSED)
            return TestResult.PASSED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_leader_failure(self) -> TestResult:
        """Тест 6: Отказ лидера - проверка перевыбора лидера в случае его недоступности."""
        test = self.add_test_case(
            "leader_failure",
            "Проверка перевыбора лидера при его недоступности"
        )
        test.start()

        try:
            # Находим текущего лидера
            initial_leader = self.cluster.wait_for_leader_election(10)
            if not initial_leader:
                test.log("ERROR: No initial leader found")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"Initial leader: {initial_leader}")

            # Записываем данные для проверки консистентности
            success, message = self.api_client.put("leader_fail_key", "leader_fail_value")
            if not success:
                test.log(f"ERROR: Failed to put initial data: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Останавливаем лидера
            test.log(f"Stopping current leader: {initial_leader}")
            if not self.cluster.stop_node(initial_leader):
                test.log(f"ERROR: Failed to stop leader {initial_leader}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Ждем пока лидер станет недоступным
            if not self.cluster.wait_for_node_unavailable(initial_leader, 10):
                test.log(f"WARNING: Leader {initial_leader} is still responding after stop")

            # Ждем перевыбора лидера
            time.sleep(5)
            new_leader = self.cluster.wait_for_leader_election(20)
            if not new_leader:
                test.log("ERROR: No new leader elected after leader failure")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            if new_leader == initial_leader:
                test.log("ERROR: Same leader elected after failure (should be different)")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"SUCCESS: New leader elected: {new_leader}")

            # Проверяем, что данные все еще доступны
            success, value = self.api_client.get("leader_fail_key")
            if not (success and value == "leader_fail_value"):
                test.log(f"ERROR: Data lost during leader transition: expected 'leader_fail_value', got '{value}'")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Проверяем, что можем записывать новые данные
            success, message = self.api_client.put("leader_fail_key2", "leader_fail_value2")
            if not success:
                test.log(f"ERROR: Failed to put data with new leader: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Возвращаем старого лидера
            test.log(f"Restarting old leader: {initial_leader}")
            if not self.cluster.start_node(initial_leader):
                test.log(f"WARNING: Failed to restart old leader {initial_leader}")

            test.finish(TestResult.PASSED)
            return TestResult.PASSED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_leader_return(self) -> TestResult:
        """Тест 7: Возвращение лидера - проверка синхронизации лидера с текущим состоянием кластера после его восстановления."""
        test = self.add_test_case(
            "leader_return",
            "Проверка синхронизации вернувшегося лидера с текущим состоянием кластера"
        )
        test.start()

        try:
            # Находим текущего лидера
            current_leader = self.cluster.wait_for_leader_election(10)
            if not current_leader:
                test.log("ERROR: No current leader found")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"Current leader: {current_leader}")

            # Записываем данные
            success, message = self.api_client.put("leader_return_key1", "leader_return_value1")
            if not success:
                test.log(f"ERROR: Failed to put initial data: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Останавливаем лидера
            test.log(f"Stopping current leader: {current_leader}")
            if not self.cluster.stop_node(current_leader):
                test.log(f"ERROR: Failed to stop leader {current_leader}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Ждем пока лидер станет недоступным
            if not self.cluster.wait_for_node_unavailable(current_leader, 10):
                test.log(f"WARNING: Leader {current_leader} is still responding after stop")

            # Ждем нового лидера
            time.sleep(5)
            new_leader = self.cluster.wait_for_leader_election(15)
            if not new_leader or new_leader == current_leader:
                test.log("ERROR: Invalid new leader after stopping current leader")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"New leader: {new_leader}")

            # Записываем новые данные под новым лидером
            success, message = self.api_client.put("leader_return_key2", "leader_return_value2")
            if not success:
                test.log(f"ERROR: Failed to put data with new leader: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Возвращаем старого лидера
            test.log(f"Restarting old leader: {current_leader}")
            if not self.cluster.start_node(current_leader):
                test.log(f"ERROR: Failed to restart old leader {current_leader}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Ждем синхронизации
            time.sleep(10)

            # Проверяем, что старый лидер теперь имеет все данные
            for key, expected_value in [("leader_return_key1", "leader_return_value1"),
                                       ("leader_return_key2", "leader_return_value2")]:
                success, value = self.api_client.get(key)
                if not (success and value == expected_value):
                    test.log(f"ERROR: Leader {current_leader} not synchronized: {key} expected '{expected_value}', got '{value}'")
                    test.finish(TestResult.FAILED)
                    return TestResult.FAILED

            test.log(f"SUCCESS: Leader {current_leader} successfully synchronized with cluster state")

            test.finish(TestResult.PASSED)
            return TestResult.PASSED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_follower_return(self) -> TestResult:
        """Тест 8: Возвращение ноды - проверка синхронизации обычной ноды(follower) с текущим состоянием кластера после ее восстановления."""
        test = self.add_test_case(
            "follower_return",
            "Проверка синхронизации вернувшейся ноды follower с текущим состоянием кластера"
        )
        test.start()

        try:
            # Находим текущего лидера
            leader = self.cluster.wait_for_leader_election(10)
            if not leader:
                test.log("ERROR: No leader found")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"Current leader: {leader}")

            # Записываем данные
            success, message = self.api_client.put("follower_return_key1", "follower_return_value1")
            if not success:
                test.log(f"ERROR: Failed to put initial data: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Выбираем follower для остановки
            follower_to_stop = "node4" if leader != "node4" else "node3"
            test.log(f"Stopping follower: {follower_to_stop}")

            if not self.cluster.stop_node(follower_to_stop):
                test.log(f"ERROR: Failed to stop follower {follower_to_stop}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Ждем пока follower станет недоступным
            if not self.cluster.wait_for_node_unavailable(follower_to_stop, 10):
                test.log(f"WARNING: Follower {follower_to_stop} is still responding after stop")

            # Ждем стабилизации кластера
            time.sleep(15)

            # Записываем новые данные пока follower недоступен
            success, message = self.api_client.put("follower_return_key2", "follower_return_value2")
            if not success:
                test.log(f"ERROR: Failed to put data while follower is down: {message}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Возвращаем follower
            test.log(f"Restarting follower: {follower_to_stop}")
            if not self.cluster.start_node(follower_to_stop):
                test.log(f"ERROR: Failed to restart follower {follower_to_stop}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Ждем синхронизации
            time.sleep(15)

            # Проверяем, что follower теперь имеет все данные
            for key, expected_value in [("follower_return_key1", "follower_return_value1"),
                                       ("follower_return_key2", "follower_return_value2")]:
                success, value = self.api_client.get(key)
                if not (success and value == expected_value):
                    test.log(f"ERROR: Follower {follower_to_stop} not synchronized: {key} expected '{expected_value}', got '{value}'")
                    test.finish(TestResult.FAILED)
                    return TestResult.FAILED

            test.log(f"SUCCESS: Follower {follower_to_stop} successfully synchronized with cluster state")

            test.finish(TestResult.PASSED)
            return TestResult.PASSED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def run_test_data_persistence(self) -> TestResult:
        """Тест 9: Персистентность данных - проверка целостности и доступности данных после смены лидера."""
        test = self.add_test_case(
            "data_persistence",
            "Проверка целостности и доступности данных после смены лидера"
        )
        test.start()

        try:
            # Находим текущего лидера
            initial_leader = self.cluster.wait_for_leader_election(10)
            if not initial_leader:
                test.log("ERROR: No initial leader found")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"Initial leader: {initial_leader}")

            # Записываем большой набор тестовых данных
            test_data = {}
            for i in range(10):
                key = f"persistence_key_{i}"
                value = f"persistence_value_{i}_with_timestamp_{time.time()}"
                test_data[key] = value

                success, message = self.api_client.put(key, value)
                if not success:
                    test.log(f"ERROR: Failed to put {key}: {message}")
                    test.finish(TestResult.FAILED)
                    return TestResult.FAILED

            test.log(f"SUCCESS: Stored {len(test_data)} key-value pairs")

            # Принудительно меняем лидера, останавливая текущего
            test.log(f"Forcing leader change by stopping {initial_leader}")
            if not self.cluster.stop_node(initial_leader):
                test.log(f"ERROR: Failed to stop leader {initial_leader}")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            # Ждем нового лидера
            time.sleep(15)
            new_leader = self.cluster.wait_for_leader_election(20)
            if not new_leader:
                test.log("ERROR: No new leader elected")
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

            test.log(f"New leader: {new_leader}")

            # Проверяем целостность всех данных
            data_intact = True
            for key, expected_value in test_data.items():
                success, value = self.api_client.get(key)
                if success and value == expected_value:
                    test.log(f"SUCCESS: {key} preserved correctly")
                else:
                    test.log(f"ERROR: {key} corrupted or lost: expected '{expected_value}', got '{value}'")
                    data_intact = False

            # Записываем дополнительные данные под новым лидером
            success, message = self.api_client.put("persistence_key_new", "persistence_value_new")
            if not success:
                test.log(f"ERROR: Failed to put new data under new leader: {message}")
                data_intact = False

            if data_intact:
                test.log("SUCCESS: All data persisted correctly through leader change")
                test.finish(TestResult.PASSED)
                return TestResult.PASSED
            else:
                test.finish(TestResult.FAILED)
                return TestResult.FAILED

        except Exception as e:
            test.log(f"ERROR: Test failed with exception: {str(e)}")
            test.finish(TestResult.FAILED)
            return TestResult.FAILED

    def generate_report(self) -> str:
        """Генерирует отчет в формате Markdown."""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        report = f"""# Отчет интеграционного тестирования Raft кластера

**Дата и время выполнения:** {timestamp}

## Сводка результатов

"""

        # Подсчет результатов
        passed = sum(1 for test in self.test_cases if test.result == TestResult.PASSED)
        failed = sum(1 for test in self.test_cases if test.result == TestResult.FAILED)
        skipped = sum(1 for test in self.test_cases if test.result == TestResult.SKIPPED)
        total = len(self.test_cases)

        report += f"""
| Статус | Количество |
|--------|------------|
| PASS | {passed} |
| FAIL | {failed} |
| SKIP | {skipped} |
| **Всего** | **{total}** |

"""

        # Детальные результаты
        report += "\n## Детальные результаты тестов\n\n"

        for test in self.test_cases:
            status_text = {
                TestResult.PASSED: "[PASS]",
                TestResult.FAILED: "[FAIL]",
                TestResult.SKIPPED: "[SKIP]"
            }[test.result]

            duration = ""
            if test.start_time and test.end_time:
                duration = f" ({test.end_time - test.start_time:.2f}s)"

            report += f"### {status_text} {test.name}{duration}\n\n"
            report += f"{test.description}\n\n"

            if test.logs:
                report += "**Логи выполнения:**\n\n```\n"
                for log in test.logs:
                    report += f"{log}\n"
                report += "```\n\n"

        return report

    def run_all_tests(self) -> Dict[str, int]:
        """Запускает все тесты и возвращает статистику."""
        logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

        print(">>> Начинаем интеграционное тестирование Raft кластера...")

        try:
            # Тест 1: Запуск кластера и выбор лидера
            self.run_test_startup_and_leader_election()

            # Тест 2: Репликация данных
            self.run_test_data_replication()

            # Тест 3: Выживание большинства
            self.run_test_majority_survival()

            # Тест 4: Граничный случай - отсутствие кворума (2 из 4)
            self.run_test_edge_case_no_quorum()

            # Тест 5: Отсутствие кворума (1 из 4)
            self.run_test_insufficient_quorum()

            # Тест 6: Отказ лидера
            self.run_test_leader_failure()

            # Тест 7: Возвращение лидера
            self.run_test_leader_return()

            # Тест 8: Возвращение follower
            self.run_test_follower_return()

            # Тест 9: Персистентность данных
            self.run_test_data_persistence()

        finally:
            # Останавливаем кластер
            print(">>> Останавливаем кластер...")
            self.cluster.stop_cluster()

        # Генерируем отчет
        report = self.generate_report()
        with open("raft_integration_test_report.md", "w", encoding="utf-8") as f:
            f.write(report)

        print(">>> Отчет сохранен в файл: raft_integration_test_report.md")

        # Возвращаем статистику
        results = {
            "passed": sum(1 for test in self.test_cases if test.result == TestResult.PASSED),
            "failed": sum(1 for test in self.test_cases if test.result == TestResult.FAILED),
            "skipped": sum(1 for test in self.test_cases if test.result == TestResult.SKIPPED),
            "total": len(self.test_cases)
        }

        print("\n=== Итоговая статистика ===")
        print(f"   [PASS] Пройдено: {results['passed']}")
        print(f"   [FAIL] Провалено: {results['failed']}")
        print(f"   [SKIP] Пропущено: {results['skipped']}")
        print(f"   [TOTAL] Всего: {results['total']}")

        return results


def main():
    """Основная функция для запуска тестов."""
    test_runner = RaftIntegrationTest()
    results = test_runner.run_all_tests()

    # Выходим с кодом ошибки, если есть проваленные тесты
    if results['failed'] > 0:
        exit(1)


if __name__ == "__main__":
    main()
