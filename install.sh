#!/bin/bash
set -euo pipefail

# Install a desktop entry for TokiFactor under ~/.local, wrapping run.sh:
#
#   ~/.local/share/applications/tokifactor.desktop   menu / app-grid entry
#   ~/.local/share/icons/hicolor/<size>/apps/tokifactor.png
#   ~/.local/bin/tokifactor                           launcher that runs run.sh
#
# Usage:
#   ./install.sh              install (or refresh) the desktop entry
#   ./install.sh -u           remove everything this script installed
#   ./install.sh -h           show this help
#
# XDG_DATA_HOME / XDG_BIN_HOME override the target locations.

APP_ID="tokifactor"
APP_NAME="TokiFactor"
# Must match the running window's WM_CLASS so the shell pairs window and icon.
WM_CLASS="com-acite-tokifactor-MainKt"
ICON_SIZES=(128 256 512)

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_SH="$REPO_ROOT/run.sh"
ICON_SRC="$REPO_ROOT/desktopApp/icons/icon.png"

DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
DESKTOP_DIR="$DATA_HOME/applications"
ICON_DIR="$DATA_HOME/icons/hicolor"
BIN_DIR="${XDG_BIN_HOME:-$HOME/.local/bin}"

DESKTOP_FILE="$DESKTOP_DIR/$APP_ID.desktop"
LAUNCHER="$BIN_DIR/$APP_ID"

usage() {
    sed -n '4,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
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

do_install() {
    [ -f "$RUN_SH" ] || { echo "install.sh: $RUN_SH is missing" >&2; exit 1; }
    [ -f "$ICON_SRC" ] || { echo "install.sh: $ICON_SRC is missing" >&2; exit 1; }

    mkdir -p "$DESKTOP_DIR" "$BIN_DIR"

    # Launcher goes through `bash` so run.sh does not need its executable bit.
    install -Dm755 /dev/null "$LAUNCHER"
    cat >"$LAUNCHER" <<EOF
#!/bin/bash
exec bash "$RUN_SH" "\$@"
EOF

    install -Dm755 /dev/null "$DESKTOP_FILE"
    cat >"$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Version=1.0
Name=$APP_NAME
GenericName=Encrypted messenger
Comment=Send encrypted text and images over a public MQTT broker
Exec=$LAUNCHER
Icon=$APP_ID
Terminal=false
Categories=Network;Chat;InstantMessaging;
Keywords=chat;mqtt;encrypted;$APP_ID;
StartupNotify=true
StartupWMClass=$WM_CLASS
EOF

    install_icon
    refresh_caches

    if command -v desktop-file-validate >/dev/null 2>&1; then
        desktop-file-validate "$DESKTOP_FILE" || echo "install.sh: desktop-file-validate reported issues above" >&2
    fi

    echo "install.sh: installed"
    echo "  entry   $DESKTOP_FILE"
    echo "  icon    $ICON_DIR/<size>/apps/$APP_ID.png (sizes: ${ICON_SIZES[*]})"
    echo "  command $LAUNCHER"

    case ":$PATH:" in
        *":$BIN_DIR:"*) ;;
        *) echo "install.sh: note: $BIN_DIR is not on PATH; the menu entry still works." ;;
    esac
}

do_uninstall() {
    local removed=0 path
    for path in "$DESKTOP_FILE" "$LAUNCHER"; do
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
