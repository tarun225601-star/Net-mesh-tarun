#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# sync-and-push.sh
# Merges remote divergence and pushes local VPN code to GitHub.
# Run from the workspace root:
#   GH_TOKEN=your_token bash sync-and-push.sh
# ─────────────────────────────────────────────────────────────
set -e

REPO="https://github.com/tarun225601-star/NetMesh-Service"
BRANCH="main"

if [ -z "$GH_TOKEN" ]; then
  echo "ERROR: set GH_TOKEN before running this script."
  echo "  export GH_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx"
  echo "  bash sync-and-push.sh"
  exit 1
fi

AUTH_URL="https://tarun225601-star:${GH_TOKEN}@github.com/tarun225601-star/NetMesh-Service"

# ── 1. Point remote at authenticated URL ──────────────────────
echo "[1/5] Configuring remote..."
git remote set-url origin "$AUTH_URL"

# ── 2. Fetch all remote history ────────────────────────────────
echo "[2/5] Fetching remote..."
git fetch origin

# ── 3. Merge remote into local, allowing unrelated histories ───
#       Strategy: ours — our local VPN files win on every conflict
echo "[3/5] Merging (local code wins on conflicts)..."
git merge origin/"$BRANCH" \
    --allow-unrelated-histories \
    --strategy-option=ours \
    -m "Merge: integrate remote history, keep local VPN implementation"

# ── 4. Stage any untracked merge artefacts and commit if needed
echo "[4/5] Checking for uncommitted changes..."
if ! git diff --cached --quiet; then
  git commit -m "Merge: resolve remaining conflicts in favour of local VPN code"
fi

# ── 5. Push ────────────────────────────────────────────────────
echo "[5/5] Pushing to origin/$BRANCH..."
git push origin "$BRANCH"

# ── 6. Restore remote URL without token (security hygiene) ─────
git remote set-url origin "$REPO"

echo ""
echo "✅  Done. All commits pushed to $REPO"
echo "    Open the Actions tab to watch the debug APK build."
