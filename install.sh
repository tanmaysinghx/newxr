#!/usr/bin/env sh
# Installs Newxr as a systemd service on Linux.
#
#   sudo ./install.sh [--jar dist/newxr.jar] [--port 8080] [--home /var/lib/newxr] [--user newxr]
#
# Creates a system user, copies the jar to /opt/newxr, keeps data in the Newxr home, and starts
# newxr.service. Re-run it to upgrade: the jar is replaced and data is left alone.
set -eu

JAR="dist/newxr.jar"; PORT=8080; HOME_DIR=/var/lib/newxr; SVC_USER=newxr; JAVA_BIN=""
while [ $# -gt 0 ]; do
  case "$1" in
    --jar) JAR=$2; shift 2 ;;
    --port) PORT=$2; shift 2 ;;
    --home) HOME_DIR=$2; shift 2 ;;
    --user) SVC_USER=$2; shift 2 ;;
    --java) JAVA_BIN=$2; shift 2 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[ "$(id -u)" -eq 0 ] || { echo "error: run as root (sudo $0 ...)" >&2; exit 1; }
command -v systemctl >/dev/null 2>&1 || { echo "error: systemd is required" >&2; exit 1; }
[ -f "$JAR" ] || { echo "error: $JAR not found — run ./build.sh first or pass --jar" >&2; exit 1; }
case "$PORT" in ''|*[!0-9]*) echo "error: --port must be a number" >&2; exit 2 ;; esac

JAVA_BIN=${JAVA_BIN:-$(command -v java || true)}
[ -n "$JAVA_BIN" ] || { echo "error: Java 25 is required (install a JDK/JRE 25, or pass --java /path/to/java)" >&2; exit 1; }
JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
[ "${JAVA_MAJOR:-0}" -ge 25 ] || { echo "error: Java 25 or newer is required, found ${JAVA_MAJOR:-unknown} at $JAVA_BIN" >&2; exit 1; }

if ! id "$SVC_USER" >/dev/null 2>&1; then
  echo "==> Creating system user $SVC_USER"
  useradd --system --home-dir "$HOME_DIR" --shell /usr/sbin/nologin "$SVC_USER"
fi

echo "==> Installing jar to /opt/newxr"
install -d -m 755 /opt/newxr
install -m 644 "$JAR" /opt/newxr/newxr.jar
install -d -m 750 -o "$SVC_USER" -g "$SVC_USER" "$HOME_DIR"

echo "==> Writing /etc/systemd/system/newxr.service"
SRC=$(dirname "$0")/distribution/newxr.service
sed -e "s#@JAVA@#$JAVA_BIN#" -e "s#@USER@#$SVC_USER#g" -e "s#@HOME@#$HOME_DIR#g" -e "s#@PORT@#$PORT#g" "$SRC" > /etc/systemd/system/newxr.service

systemctl daemon-reload
systemctl enable newxr.service >/dev/null
systemctl restart newxr.service

echo "==> Newxr is starting on port $PORT (data in $HOME_DIR)"
echo "    Logs:       journalctl -u newxr -f"
echo "    Setup key:  sudo cat $HOME_DIR/secrets/initialSetupKey   (first start only)"
echo "    Then open http://<this-server>:$PORT and follow the setup wizard."
