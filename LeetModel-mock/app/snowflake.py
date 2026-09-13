from __future__ import annotations

import time
from typing import Optional


class Snowflake:
    """标准 64 位雪花算法生成器 (对齐 MyBatis-Plus Sequence 与 Twitter Snowflake 规范)"""

    # 纪元时间戳: 2010-11-04 01:42:54.657 GMT (MyBatis-Plus 默认 twepoch)
    TWEPOCH = 1288834974657

    WORKER_ID_BITS = 5
    DATACENTER_ID_BITS = 5
    SEQUENCE_BITS = 12

    MAX_WORKER_ID = -1 ^ (-1 << WORKER_ID_BITS)  # 31
    MAX_DATACENTER_ID = -1 ^ (-1 << DATACENTER_ID_BITS)  # 31
    SEQUENCE_MASK = -1 ^ (-1 << SEQUENCE_BITS)  # 4095

    WORKER_ID_SHIFT = SEQUENCE_BITS  # 12
    DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS  # 17
    TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS  # 22

    def __init__(self, datacenter_id: int = 1, worker_id: int = 1):
        if datacenter_id > self.MAX_DATACENTER_ID or datacenter_id < 0:
            raise ValueError(f"datacenter_id must be between 0 and {self.MAX_DATACENTER_ID}")
        if worker_id > self.MAX_WORKER_ID or worker_id < 0:
            raise ValueError(f"worker_id must be between 0 and {self.MAX_WORKER_ID}")
        self.datacenter_id = datacenter_id
        self.worker_id = worker_id
        self.sequence = 0
        self.last_timestamp = -1

    def _time_gen(self) -> int:
        return int(time.time() * 1000)

    def _til_next_millis(self, last_timestamp: int) -> int:
        timestamp = self._time_gen()
        while timestamp <= last_timestamp:
            timestamp = self._time_gen()
        return timestamp

    def next_id(self, custom_timestamp_ms: Optional[int] = None) -> int:
        if custom_timestamp_ms is not None:
            timestamp = custom_timestamp_ms
            if timestamp == self.last_timestamp:
                self.sequence = (self.sequence + 1) & self.SEQUENCE_MASK
            else:
                self.sequence = 0
            self.last_timestamp = timestamp
        else:
            timestamp = self._time_gen()
            if timestamp < self.last_timestamp:
                timestamp = self.last_timestamp
            if timestamp == self.last_timestamp:
                self.sequence = (self.sequence + 1) & self.SEQUENCE_MASK
                if self.sequence == 0:
                    timestamp = self._til_next_millis(self.last_timestamp)
            else:
                self.sequence = 0
            self.last_timestamp = timestamp

        timestamp_offset = timestamp - self.TWEPOCH
        snowflake_id = (
            (timestamp_offset << self.TIMESTAMP_LEFT_SHIFT)
            | (self.datacenter_id << self.DATACENTER_ID_SHIFT)
            | (self.worker_id << self.WORKER_ID_SHIFT)
            | self.sequence
        )
        return snowflake_id

    def next_ids(self, count: int, custom_timestamp_ms: Optional[int] = None) -> list[int]:
        return [self.next_id(custom_timestamp_ms) for _ in range(count)]


default_snowflake = Snowflake(datacenter_id=1, worker_id=1)
