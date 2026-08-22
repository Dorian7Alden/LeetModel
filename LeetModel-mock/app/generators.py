from __future__ import annotations

import random
from datetime import date
from typing import Any

import bcrypt
from faker import Faker

DEFAULT_SEED = 20260822


def make_faker(locale: str, seed: int | None = DEFAULT_SEED) -> Faker:
    fake = Faker(locale)
    if seed is not None:
        fake.seed_instance(seed)
    return fake


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def _filter_unique(items: list[str], min_length: int, max_length: int, count: int) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in items:
        if len(item) < min_length or len(item) > max_length:
            continue
        if item in seen:
            continue
        seen.add(item)
        result.append(item)
        if len(result) >= count:
            break
    return result


def gen_chinese_names(
    count: int, min_length: int = 2, max_length: int = 3, seed: int | None = DEFAULT_SEED
) -> list[str]:
    fake = make_faker("zh_CN", seed)
    return _filter_unique(
        [fake.name() for _ in range(count * 20)], min_length, max_length, count
    )


def gen_english_names(
    count: int, min_length: int = 4, max_length: int = 24, seed: int | None = DEFAULT_SEED
) -> list[str]:
    fake = make_faker("en_US", seed)
    items = [f"{fake.first_name()} {fake.last_name()}" for _ in range(count * 20)]
    return _filter_unique(items, min_length, max_length, count)


def gen_usernames(
    count: int, min_length: int = 4, max_length: int = 16, seed: int | None = DEFAULT_SEED
) -> list[str]:
    fake = make_faker("zh_CN", seed)
    items = [fake.user_name() for _ in range(count * 20)]
    return _filter_unique(items, min_length, max_length, count)


def gen_emails(count: int, seed: int | None = DEFAULT_SEED) -> list[str]:
    fake = make_faker("zh_CN", seed)
    return [fake.email() for _ in range(count)]


def gen_integers(
    count: int, min_value: int = 0, max_value: int = 100, seed: int | None = DEFAULT_SEED
) -> list[int]:
    rng = random.Random(seed)
    return [rng.randint(min_value, max_value) for _ in range(count)]


def gen_decimals(
    count: int,
    min_value: float = 0.0,
    max_value: float = 1.0,
    precision: int = 2,
    seed: int | None = DEFAULT_SEED,
) -> list[float]:
    rng = random.Random(seed)
    factor = 10**precision
    return [
        round(rng.uniform(min_value, max_value) * factor) / factor
        for _ in range(count)
    ]


def gen_dates(
    count: int,
    start: str = "2020-01-01",
    end: str = "2026-12-31",
    seed: int | None = DEFAULT_SEED,
) -> list[str]:
    fake = make_faker("zh_CN", seed)
    return [
        fake.date_between(start_date=date.fromisoformat(start), end_date=date.fromisoformat(end)).isoformat()
        for _ in range(count)
    ]


def gen_datetimes(
    count: int,
    start: str = "2020-01-01 00:00:00",
    end: str = "2026-12-31 23:59:59",
    seed: int | None = DEFAULT_SEED,
) -> list[str]:
    fake = make_faker("zh_CN", seed)
    return [
        fake.date_time_between(start_date=date.fromisoformat(start[:10]), end_date=date.fromisoformat(end[:10])).isoformat(sep=" ", timespec="seconds")
        for _ in range(count)
    ]


def gen_words(
    count: int,
    min_length: int = 2,
    max_length: int = 12,
    locale: str = "zh_CN",
    seed: int | None = DEFAULT_SEED,
) -> list[str]:
    fake = make_faker(locale, seed)
    return _filter_unique([fake.word() for _ in range(count * 20)], min_length, max_length, count)


def gen_sentences(
    count: int,
    min_length: int = 5,
    max_length: int = 60,
    locale: str = "zh_CN",
    seed: int | None = DEFAULT_SEED,
) -> list[str]:
    fake = make_faker(locale, seed)
    return _filter_unique([fake.sentence() for _ in range(count * 20)], min_length, max_length, count)


def gen_plain_passwords(
    count: int, length: int = 12, seed: int | None = DEFAULT_SEED
) -> list[str]:
    rng = random.Random(seed)
    chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"
    return ["".join(rng.choice(chars) for _ in range(length)) for _ in range(count)]


def gen_bcrypt_passwords(count: int, password: str | None = None) -> list[dict[str, str]]:
    return [
        {
            "plain": password or gen_plain_passwords(1, length=12, seed=DEFAULT_SEED + index)[0],
            "hash": hash_password(password or gen_plain_passwords(1, length=12, seed=DEFAULT_SEED + index)[0]),
        }
        for index in range(count)
    ]


def gen_ids(start: int = 1, count: int = 10) -> list[int]:
    return list(range(start, start + count))


def gen_avatars(
    count: int, style: str = "micah", seed: int | None = None
) -> list[str]:
    if seed is None:
        seed = random.Random().randint(0, 999999)
    return [
        f"https://api.dicebear.com/9.x/{style}/svg?seed={seed}-{index}"
        for index in range(count)
    ]


def gen_urls(count: int, seed: int | None = DEFAULT_SEED) -> list[str]:
    fake = make_faker("zh_CN", seed)
    return [fake.url() for _ in range(count)]


def gen_phone_numbers(count: int, seed: int | None = DEFAULT_SEED) -> list[str]:
    fake = make_faker("zh_CN", seed)
    return [fake.phone_number() for _ in range(count)]


def gen_booleans(count: int, seed: int | None = DEFAULT_SEED) -> list[bool]:
    rng = random.Random(seed)
    return [rng.choice([True, False]) for _ in range(count)]
