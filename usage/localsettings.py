import os


SENTRY_AUTH_TOKEN = os.environ.get("SENTRY_AUTH_TOKEN", "")
SENTRY_ISSUE_ID = os.environ.get("SENTRY_ISSUE_ID", "missing")

S3_BUCKET = os.environ.get("S3_BUCKET", "androidautoidrive")
S3_PATH = os.environ.get("S3_PATH", "usage")


del os
