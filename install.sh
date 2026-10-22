#!/bin/bash
set -euo pipefail

# Install TokiFactor for the current Linux user under the XDG directories:
#
#   ~/.local/share/applications/tokifactor.desktop   menu / app-grid entry
#   ~/.local/share/icons/hicolor/<size>/apps/tokifactor.png
#   ~/.local/bin/tokifactor                           desktop launcher
#   ~/.local/bin/tokifactord                          daemon launcher
#   ~/.local/share/tokifactor/                        installed daemon jar + run.sh
#   ~/.config/systemd/user/tokifactord.service        systemd --user unit
#
# Usage:
#   ./install.sh              install (or refresh) the desktop app and daemon
#   ./install.sh -u           remove everything this script installed
#   ./install.sh -h           show this help
#
# XDG_DATA_HOME / XDG_BIN_HOME / XDG_CONFIG_HOME override the target locations.
# Chat data under ~/.tokifactor is never removed by -u.

APP_ID="tokifactor"
APP_NAME="TokiFactor"
DAEMON_ID="tokifactord"
DAEMON_NAME="TokiFactor MQTT daemon"
# Must match the running window's WM_CLASS so the shell pairs window and icon.
WM_CLASS="com-acite-tokifactor-MainKt"
ICON_SIZES=(128 256 512)

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_SH="$REPO_ROOT/run.sh"
ICON_SRC="$REPO_ROOT/desktopApp/icons/icon.png"
JAR_DIR="$REPO_ROOT/desktopApp/build/compose/jars"

DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
CONFIG_HOME="${XDG_CONFIG_HOME:-$HOME/.config}"
DESKTOP_DIR="$DATA_HOME/applications"
ICON_DIR="$DATA_HOME/icons/hicolor"
BIN_DIR="${XDG_BIN_HOME:-$HOME/.local/bin}"
DAEMON_DIR="$DATA_HOME/tokifactor"
SYSTEMD_USER_DIR="$CONFIG_HOME/systemd/user"

DESKTOP_FILE="$DESKTOP_DIR/$APP_ID.desktop"
LAUNCHER="$BIN_DIR/$APP_ID"
DAEMON_LAUNCHER="$BIN_DIR/$DAEMON_ID"
DAEMON_RUN="$DAEMON_DIR/run.sh"
DAEMON_JAR="$DAEMON_DIR/tokifactord.jar"
UNIT_FILE="$SYSTEMD_USER_DIR/$DAEMON_ID.service"

usage() {
    sed -n '4,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

refresh_caches() {
    if [ -d "$DESKTOP_DIR" ] && command -v update-desktop-database >/dev/null 2>&1; then
        update-desktop-database "$DESKTOP_DIR" >/dev/null 2>&1 || true
    fi
    if [ -d "$ICON_DIR" ] && command -v gtk-update-icon-cache >/dev/null 2>&1; then
        gtk-update-icon-cache -f -t "$ICON_DIR" >/dev/null 2>&1 || true
    fi
}

install_icon() {
    local size dest_dir dest
    for size in "${ICON_SIZES[@]}"; do
        dest_dir="$ICON_DIR/${size}x${size}/apps"
        dest="$dest_dir/$APP_ID.png"
        mkdir -p "$dest_dir"
        if [ "$size" -eq 512 ] || [ -z "$RESIZER" ]; then
            install -m 644 "$ICON_SRC" "$dest"
        else
            "$RESIZER" "$ICON_SRC" -resize "${size}x${size}" "$dest"
        fi
    done
}

package_daemon_jar() {
    echo "install.sh: packaging the TokiFactor daemon jar..."
    (cd "$REPO_ROOT" && ./gradlew :desktopApp:packageUberJarForCurrentOS --no-daemon)
    local jar
    jar="$(ls -t "$JAR_DIR"/*.jar 2>/dev/null | head -n 1 || true)"
    if [ -z "$jar" ] || [ ! -f "$jar" ]; then
        echo "install.sh: packaging produced no jar under $JAR_DIR" >&2
        exit 1
    fi
    install -Dm644 "$jar" "$DAEMON_JAR"
    install -Dm755 "$RUN_SH" "$DAEMON_RUN"
}

install_daemon_launcher() {
    local jar_q run_q
    jar_q="$(printf '%q' "$DAEMON_JAR")"
    run_q="$(printf '%q' "$DAEMON_RUN")"
    install -Dm755 /dev/null "$DAEMON_LAUNCHER"
    {
        echo '#!/bin/bash'
        echo "export TOKIFACTOR_JAR=$jar_q"
        echo "exec bash $run_q --daemon"
    } >"$DAEMON_LAUNCHER"
}

systemd_quote() {
    printf '"%s"' "$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g')"
}

install_systemd_unit() {
    local exec_start
    exec_start="$(systemd_quote "$DAEMON_LAUNCHER")"
    mkdir -p "$SYSTEMD_USER_DIR"
    install -Dm644 /dev/null "$UNIT_FILE"
    {
        echo '[Unit]'
        echo "Description=$DAEMON_NAME"
        echo 'After=network-online.target'
        echo 'Wants=network-online.target'
        echo ''
        echo '[Service]'
        echo 'Type=simple'
        echo "ExecStart=$exec_start"
        echo 'Restart=on-failure'
        echo 'RestartSec=3'
        echo 'Environment=DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/%U/bus'
        echo ''
        echo '[Install]'
        echo 'WantedBy=default.target'
    } >"$UNIT_FILE"
}

enable_daemon() {
    if ! command -v systemctl >/dev/null 2>&1; then
        echo "install.sh: systemctl is unavailable; install the unit manually or use a systemd Linux." >&2
        return 1
    fi
    if ! systemctl --user daemon-reload; then
        echo "install.sh: could not reach the systemd user manager; run this from your graphical login session." >&2
        return 1
    fi
    if ! systemctl --user enable "$DAEMON_ID.service"; then
        echo "install.sh: failed to enable $DAEMON_ID.service." >&2
        return 1
    fi
    if ! systemctl --user restart "$DAEMON_ID.service"; then
        echo "install.sh: failed to start $DAEMON_ID.service; inspect: journalctl --user -u $DAEMON_ID.service" >&2
        return 1
    fi
    if ! systemctl --user is-active --quiet "$DAEMON_ID.service"; then
        echo "install.sh: $DAEMON_ID.service is not active; inspect: journalctl --user -u $DAEMON_ID.service" >&2
        return 1
    fi
}

do_install() {
    [ -f "$RUN_SH" ] || { echo "install.sh: $RUN_SH is missing" >&2; exit 1; }
    [ -f "$ICON_SRC" ] || { echo "install.sh: $ICON_SRC is missing" >&2; exit 1; }

    mkdir -p "$DESKTOP_DIR" "$BIN_DIR" "$DAEMON_DIR"

    # Launcher goes through `bash` so run.sh does not need its executable bit.
    install -Dm755 /dev/null "$LAUNCHER"
    {
        echo '#!/bin/bash'
        echo "exec bash $(printf '%q' "$RUN_SH") \"\$@\""
    } >"$LAUNCHER"

    install -Dm755 /dev/null "$DESKTOP_FILE"
    {
        echo '[Desktop Entry]'
        echo 'Type=Application'
        echo 'Version=1.0'
        echo "Name=$APP_NAME"
        echo 'GenericName=Encrypted messenger'
        echo 'Comment=Send encrypted text and images over a public MQTT broker'
        echo "Exec=$LAUNCHER"
        echo "Icon=$APP_ID"
        echo 'Terminal=false'
        echo 'Categories=Network;Chat;InstantMessaging;'
        echo "Keywords=chat;mqtt;encrypted;$APP_ID;"
        echo 'StartupNotify=true'
        echo "StartupWMClass=$WM_CLASS"
    } >"$DESKTOP_FILE"

    install_icon
    refresh_caches

    if command -v desktop-file-validate >/dev/null 2>&1; then
        desktop-file-validate "$DESKTOP_FILE" || echo "install.sh: desktop-file-validate reported issues above" >&2
    fi

    package_daemon_jar
    install_daemon_launcher
    install_systemd_unit

    if ! command -v notify-send >/dev/null 2>&1; then
        echo "install.sh: warning: notify-send is missing; daemon message notifications will be disabled." >&2
        echo "install.sh: install libnotify-bin (Debian/Ubuntu) or libnotify (Fedora/Arch) to enable them." >&2
    fi

    local daemon_ok=0
    enable_daemon || daemon_ok=1

    echo "install.sh: installed"
    echo "  entry    $DESKTOP_FILE"
    echo "  icon     $ICON_DIR/<size>/apps/$APP_ID.png (sizes: ${ICON_SIZES[*]})"
    echo "  command  $LAUNCHER"
    echo "  daemon   $DAEMON_LAUNCHER"
    echo "  service  $UNIT_FILE"

    case ":$PATH:" in
        *":$BIN_DIR:"*) ;;
        *) echo "install.sh: note: $BIN_DIR is not on PATH; the menu entry still works." ;;
    esac

    if [ "$daemon_ok" -ne 0 ]; then
        echo "install.sh: daemon files are installed, but the user service is not running." >&2
        exit 1
    fi
}

do_uninstall() {
    local removed=0 path

    if command -v systemctl >/dev/null 2>&1; then
        systemctl --user disable --now "$DAEMON_ID.service" >/dev/null 2>&1 || true
    fi

    for path in "$UNIT_FILE" "$DESKTOP_FILE" "$LAUNCHER" "$DAEMON_LAUNCHER" "$DAEMON_JAR" "$DAEMON_RUN"; do
        if [ -e "$path" ]; then
            rm -f "$path"
            echo "install.sh: removed $path"
            removed=1
        fi
    done
    for path in "$ICON_DIR"/*/apps/"$APP_ID".png; do
        if [ -f "$path" ]; then
            rm -f "$path"
            echo "install.sh: removed $path"
            removed=1
        fi
    done
    if [ -d "$DAEMON_DIR" ]; then
        rmdir "$DAEMON_DIR" >/dev/null 2>&1 || true
    fi

    if command -v systemctl >/dev/null 2>&1; then
        systemctl --user daemon-reload >/dev/null 2>&1 || true
    fi

    if [ "$removed" -eq 0 ]; then
        echo "install.sh: nothing to remove (not installed)"
    fi
    refresh_caches
}

RESIZER=""
if command -v magick >/dev/null 2>&1; then
    RESIZER="magick"
elif command -v convert >/dev/null 2>&1; then
    RESIZER="convert"
fi

case "${1:-}" in
    ""|--install) do_install ;;
    -u|--uninstall) do_uninstall ;;
    -h|--help) usage ;;
    *) echo "install.sh: unknown option '$1'" >&2; usage >&2; exit 2 ;;
esac
