#!/data/data/com.termux/files/usr/bin/bash
#
# MUSTAKIMZX -> GitHub par ek hi command se push
#
# SETUP (ek hi baar):
#   pkg install -y git gh
#
# CHALAYEIN:
#   bash /sdcard/values/MUSTAKIMZX/push_to_github.sh
#
# Ye script: project copy karti hai -> GitHub login -> repo banati hai ->
# sab kuch push karti hai -> Actions khud chalu ho jaata hai.
#
set -e

SRC="${1:-/sdcard/values/MUSTAKIMZX}"
REPO_NAME="${2:-MUSTAKIMZX}"
WORK="$HOME/mustakimzx-git"

say() { echo ""; echo "==> $*"; }
die() { echo ""; echo "[!] $*"; exit 1; }

# ---------------------------------------------------------------- 1. tools
say "Step 1: tools check"
missing=""
for t in git gh; do
    command -v "$t" >/dev/null 2>&1 || missing="$missing $t"
done
if [ -n "$missing" ]; then
    die "Ye nahi mile:$missing
    Chalayein:  pkg install -y git gh"
fi
[ -d "$SRC" ] || die "Project nahi mila: $SRC"

# ------------------------------------------------------------- 2. copy
say "Step 2: project copy ($HOME me)"
rm -rf "$WORK"
mkdir -p "$WORK"
cp -r "$SRC"/. "$WORK"/
rm -rf "$WORK/build" "$WORK/scanner/build" "$WORK/receiver/build" "$WORK/shared/build" "$WORK/.gradle"
[ -f "$WORK/.github/workflows/build.yml" ] || die "workflow file nahi mili - .github folder check karein"
echo "copy ho gaya, files: $(find "$WORK" -type f | wc -l)"

# --------------------------------------------------------- 3. login
say "Step 3: GitHub login"
if gh auth status >/dev/null 2>&1; then
    echo "pehle se logged in: $(gh api user -q .login)"
else
    echo "Ek code screen par dikhega. Usse kholen: https://github.com/login/device"
    echo "Wahan code daalein aur Authorize dabayein. Phir yahan wapas aa jayega."
    echo ""
    echo "NOTE: script ek line par ruk sakti hai -"
    echo "      'Press Enter to open github.com in your browser'"
    echo "      Wahin termux me ENTER dabayein (ya keyboard ka enter)."
    echo ""
    # 'workflow' scope zaroori hai - iske bina .github/workflows push reject hota hai.
    # --skip-ssh-key sirf naye gh me hai; purane par fallback chalega.
    if ! gh auth login --hostname github.com --git-protocol https --web \
            --scopes workflow --skip-ssh-key; then
        echo "(purana gh version - simple login se koshish)"
        gh auth login --hostname github.com --git-protocol https --web \
            --scopes workflow || true
    fi
fi
gh auth status >/dev/null 2>&1 || die "Login poora nahi hua. Dobara chalayein."

# workflow scope check (purane login me ye nahi hota)
if ! gh auth status 2>&1 | grep -q "workflow"; then
    echo ""
    echo "workflow permission add kar rahe hain (ek baar aur authorize karna hoga)..."
    gh auth refresh --hostname github.com --scopes workflow || \
        die "workflow scope nahi mila. Chalayein: gh auth refresh -s workflow"
fi
OWNER=$(gh api user -q .login)
echo "account: $OWNER"

# https push ke liye gh ka credential helper
gh auth setup-git >/dev/null 2>&1 || true

# ------------------------------------------------------- 4. git setup
say "Step 4: git setup"
cd "$WORK"
if [ ! -d .git ]; then
    git init -q
    git symbolic-ref HEAD refs/heads/main
fi
git config user.name  >/dev/null 2>&1 || git config user.name "$OWNER"
git config user.email >/dev/null 2>&1 || git config user.email "$OWNER@users.noreply.github.com"
git add -A
if git diff --cached --quiet; then
    echo "commit karne ke liye kuch naya nahi"
else
    git commit -q -m "MUSTAKIMZX scanner + receiver"
    echo "commit ho gaya"
fi

# --------------------------------------------------- 5. repo + push
say "Step 5: repo banakar push"

# gh khali list par "" deta hai - use hata dete hain
latest_run() {
    v=$(gh run list --repo "$OWNER/$REPO_NAME" --workflow build.yml \
            --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)
    case "$v" in
        ""|"null"|"None") echo "" ;;
        *) echo "$v" ;;
    esac
}

PREV_RUN=$(latest_run)

if gh repo view "$OWNER/$REPO_NAME" >/dev/null 2>&1; then
    echo "repo pehle se hai: $OWNER/$REPO_NAME"
    git remote remove origin 2>/dev/null || true
    git remote add origin "https://github.com/$OWNER/$REPO_NAME.git"
    # agar repo GitHub par banate waqt README/gitignore add hua tha to history alag
    # hoti hai -- force se apna version upar chala jayega (hamara version hi chahiye).
    git push -u origin main --force
else
    echo "naya repo ban raha hai: $OWNER/$REPO_NAME"
    gh repo create "$REPO_NAME" --public --source=. \
        --remote=origin --push --description "MUSTAKIMZX scanner + receiver"
fi

URL="https://github.com/$OWNER/$REPO_NAME"

say "Step 6: workflow chalu karna"

RUN_ID="$PREV_RUN"
n=0
while [ "$n" -lt 6 ]; do
    sleep 5
    RUN_ID=$(latest_run)
    if [ -n "$RUN_ID" ] && [ "$RUN_ID" != "$PREV_RUN" ]; then
        echo "push se run chalu ho gaya"
        break
    fi
    n=$((n + 1))
done

# push se naya run nahi bana (kuch naya commit nahi tha) -> khud trigger karein
if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "$PREV_RUN" ]; then
    echo "naya run nahi bana, khud chalu kar rahe hain..."
    gh workflow run build.yml --repo "$OWNER/$REPO_NAME" 2>/dev/null \
        || echo "(workflow trigger nahi hua - GitHub par manually chalayein)"
    n=0
    while [ "$n" -lt 12 ]; do
        sleep 5
        RUN_ID=$(latest_run)
        if [ -n "$RUN_ID" ] && [ "$RUN_ID" != "$PREV_RUN" ]; then
            break
        fi
        n=$((n + 1))
    done
fi

# ------------------------------------------------- 7. build ka intezaar
say "Step 7: build ka intezaar (5-10 min, phone chalu rakhein)"
if [ "$RUN_ID" = "$PREV_RUN" ] && [ -n "$RUN_ID" ]; then
    echo "naya run nahi bana - purana run hi dikha rahe hain (code wahi hai to theek hai)"
fi
echo "run id: ${RUN_ID:-?}"

CONCLUSION=""
if [ -n "$RUN_ID" ]; then
    i=0
    while [ "$i" -lt 90 ]; do
        i=$((i + 1))
        STATUS=$(gh run view "$RUN_ID" --repo "$OWNER/$REPO_NAME" \
                    --json status -q '.status' 2>/dev/null || echo "")
        if [ "$STATUS" = "completed" ]; then
            CONCLUSION=$(gh run view "$RUN_ID" --repo "$OWNER/$REPO_NAME" \
                            --json conclusion -q '.conclusion' 2>/dev/null || echo "")
            break
        fi
        printf "."
        sleep 10
    done
    echo ""
fi

if [ -z "$RUN_ID" ]; then
    echo "Build abhi khatam nahi hua (ya abhi chalu hua)."
    echo "Yahan dekh lein: $URL/actions"
else
    echo "build khatam: $CONCLUSION"
fi

# ------------------------------------------------- 8. APK download
if [ "$CONCLUSION" = "success" ]; then
    say "Step 8: APK download"
    DL="$HOME/mustakimzx-apk"
    rm -rf "$DL"
    mkdir -p "$DL"
    if gh run download "$RUN_ID" --repo "$OWNER/$REPO_NAME" \
            --name MUSTAKIMZX-apks --dir "$DL" 2>/dev/null; then
        OUT="/sdcard/Download"
        mkdir -p "$OUT" 2>/dev/null || OUT="$HOME"
        for f in "$DL"/*.apk; do
            [ -f "$f" ] || continue
            cp "$f" "$OUT/$(basename "$f")"
            sz=$(du -h "$f" 2>/dev/null | cut -f1)
            echo "OK: $OUT/$(basename "$f")  (${sz:-?})"
        done
    else
        echo "download nahi ho paya. Hath se lein:"
        echo "  $URL/actions  ->  run  ->  Artifacts  ->  MUSTAKIMZX-apks"
    fi
fi

echo ""
echo "======================================================"
if [ "$CONCLUSION" = "success" ]; then
    echo " TAYYAR - APK ban gayi"
    echo ""
    echo "   /sdcard/Download/MUSTAKIMZX_scanner.apk   -> PHONE 1"
    echo "   /sdcard/Download/MUSTAKIMZX_receiver.apk  -> PHONE 2"
    echo ""
    echo " File manager -> Download -> APK par tap -> Install"
    echo " ('unknown apps' ki permission maange to Allow karein)"
elif [ -z "$RUN_ID" ]; then
    echo " BUILD ABHI CHAL RAHA HAI"
    echo ""
    echo " 5-10 min baad ye kholen:"
    echo "   $URL/actions"
    echo " sabse upar wala run -> neeche 'Artifacts' -> MUSTAKIMZX-apks"
else
    echo " BUILD FAIL: $CONCLUSION"
    echo ""
    echo " Ye kholen aur laal step ka text bhejein:"
    echo "   $URL/actions"
    echo ""
    echo " Log yahan se bhi mil sakta hai:"
    echo "   gh run view $RUN_ID --repo $OWNER/$REPO_NAME --log-failed"
fi
echo ""
echo " Dobara chalane ke liye (code badla ho to):"
echo "   bash $SRC/push_to_github.sh"
echo "======================================================"
