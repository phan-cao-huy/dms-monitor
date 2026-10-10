from app.core.security import (
    create_access_token,
    decode_access_token,
    hash_password,
    hash_refresh_token,
    verify_password,
)


def test_password_is_hashed_and_verified():
    password_hash = hash_password("correct horse battery staple")

    assert password_hash != "correct horse battery staple"
    assert password_hash.startswith("$2")
    assert verify_password("correct horse battery staple", password_hash)
    assert not verify_password("wrong password", password_hash)


def test_password_rejects_values_over_bcrypt_byte_limit():
    try:
        hash_password("é" * 37)
    except ValueError:
        pass
    else:
        raise AssertionError("Expected bcrypt byte limit to be enforced")


def test_access_token_round_trip():
    token = create_access_token(123)

    assert decode_access_token(token) == 123


def test_refresh_token_hash_is_sha256_hex():
    token_hash = hash_refresh_token("opaque-refresh-token")

    assert len(token_hash) == 64
    assert all(char in "0123456789abcdef" for char in token_hash)
