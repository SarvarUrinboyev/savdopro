"""
Unit tests for the warehouse importer's target-locking safety guards.

Pure-function tests only — NO network, NO Excel, NO production. Run with:
    PYTHONUTF8=1 python imports/test_warehouse_import.py
or under pytest:
    pytest imports/test_warehouse_import.py
"""
import base64
import json
import os
import sys

# Import the module under test (no side effects at import time besides reading env).
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import warehouse_import as wi


def make_jwt(claims):
    """Build a syntactically valid (unsigned) JWT carrying the given claims."""
    def b64(d):
        return base64.urlsafe_b64encode(json.dumps(d).encode()).decode().rstrip("=")
    return f"{b64({'alg': 'HS256'})}.{b64(claims)}.{'sig'}"


def expect_systemexit(fn, needle=None):
    try:
        fn()
    except SystemExit as e:
        if needle is not None:
            assert needle.lower() in str(e).lower(), f"missing {needle!r} in: {e}"
        return
    raise AssertionError("expected SystemExit, none raised")


# ----------------------------------------------------------------- is_production
def test_is_production():
    assert wi.is_production("http://127.0.0.1:8086") is False
    assert wi.is_production("http://localhost:8086") is False
    assert wi.is_production("http://0.0.0.0:8086") is False
    assert wi.is_production("https://167-172-164-214.nip.io") is True
    assert wi.is_production("https://app.savdopro.uz") is True


# -------------------------------------------------------------- decode_jwt_claims
def test_decode_jwt_claims():
    tok = make_jwt({"accountId": 1003, "role": "ACCOUNT_OWNER", "username": "x@y.com"})
    claims = wi.decode_jwt_claims(tok)
    assert claims["accountId"] == 1003
    assert claims["role"] == "ACCOUNT_OWNER"
    # Garbage tokens degrade to {} rather than throwing.
    assert wi.decode_jwt_claims("not-a-jwt") == {}
    assert wi.decode_jwt_claims("") == {}


# -------------------------------------------------------------------- verify_target
SHOPS_ACCT_1003 = [{"id": 6, "name": "Asosiy do'kon", "main": True}]
SHOPS_ACCT_1 = [{"id": 1, "name": "Asosiy do'kon", "main": True}]


def test_verify_target_happy_owner():
    claims = {"accountId": 1003, "role": "ACCOUNT_OWNER"}
    shop = wi.verify_target(claims, SHOPS_ACCT_1003, shop_id=6, account_id=1003, superadmin=False)
    assert shop["id"] == 6


def test_verify_target_happy_superadmin():
    claims = {"accountId": 1, "role": "SUPER_ADMIN"}
    shop = wi.verify_target(claims, SHOPS_ACCT_1, shop_id=1, account_id=1, superadmin=True)
    assert shop["id"] == 1


def test_verify_target_account_mismatch():
    # Token authenticated as account 1 but the operator declared account 1003.
    claims = {"accountId": 1, "role": "SUPER_ADMIN"}
    expect_systemexit(
        lambda: wi.verify_target(claims, SHOPS_ACCT_1, shop_id=1, account_id=1003, superadmin=False),
        needle="does not match")


def test_verify_target_superadmin_flag_but_owner_token():
    # Operator asked for super-admin mode but logged in as a plain owner.
    claims = {"accountId": 1003, "role": "ACCOUNT_OWNER"}
    expect_systemexit(
        lambda: wi.verify_target(claims, SHOPS_ACCT_1003, shop_id=6, account_id=1003, superadmin=True),
        needle="not super_admin")


def test_verify_target_shop_not_owned():
    # The classic cross-account mistake: super-admin (acct 1) aiming at shop 6,
    # which belongs to account 1003 and is therefore NOT in super-admin's /api/shops.
    claims = {"accountId": 1, "role": "SUPER_ADMIN"}
    expect_systemexit(
        lambda: wi.verify_target(claims, SHOPS_ACCT_1, shop_id=6, account_id=1, superadmin=True),
        needle="not owned")


# --------------------------------------------------------- assert_only_target_changed
def test_guard_no_drift():
    before = {1: 17, 6: 0, 2: 1}
    after = {1: 17, 6: 6598, 2: 1}      # only the target (6) grew
    assert wi.assert_only_target_changed(before, after, target_id=6) == []


def test_guard_detects_non_target_drift():
    before = {1: 17, 6: 0, 2: 1}
    after = {1: 18, 6: 6598, 2: 1}      # shop 1 leaked a product — must be caught
    drift = wi.assert_only_target_changed(before, after, target_id=6)
    assert drift == [[1, 17, 18]]


def test_guard_target_only_growth_is_ok_even_if_huge():
    before = {6: 0}
    after = {6: 6598}
    assert wi.assert_only_target_changed(before, after, target_id=6) == []


def _run_all():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    passed = 0
    for t in tests:
        t()
        print(f"  ok  {t.__name__}")
        passed += 1
    print(f"\n{passed}/{len(tests)} passed")


if __name__ == "__main__":
    _run_all()
