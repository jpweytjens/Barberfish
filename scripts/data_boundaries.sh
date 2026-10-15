#!/usr/bin/env bash
# Turn the Karoo's Data Boundaries (the borders around data cells) on or off.
#
# Usage: scripts/data_boundaries.sh on|off
#
# scripts/walk_layouts.sh turns them on for its run and off again afterwards; this script is for
# setting them by hand.

set -euo pipefail

cd "$(dirname "$0")/.."
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
source scripts/_karoo.sh

set_data_boundaries "${1:?usage: scripts/data_boundaries.sh on|off}"
