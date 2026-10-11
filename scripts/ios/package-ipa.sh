#!/usr/bin/env bash
# Packages an unsigned-for-distribution .app into an .ipa that sideloading tools
# (Sideloadly, AltStore, TrollStore, ESign…) can re-sign, and refuses to produce one
# that would fail there.
#
# usage: package-ipa.sh <path/to/App.app> <output.ipa>
set -euo pipefail

APP="$1"
OUT="$2"
NAME="$(basename "$APP" .app)"
EXE="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$APP/Info.plist")"

fail() { echo "::error::$*"; exit 1; }

echo "== Checking $APP"
[ -f "$APP/$EXE" ] || fail "main executable $EXE is missing"

# AppleDouble sidecars (._name) and .DS_Store become 'sealed resources' that vanish on the
# device and break the signature check.
find "$APP" \( -name '._*' -o -name '.DS_Store' \) -print -delete

# Every Mach-O inside the bundle must be a thin arm64 device binary. Fat binaries with
# simulator/x86 slices, and static archives dressed up as frameworks, are exactly what makes
# Sideloadly fail with "SignBinary(...): macho sign/edit failed (-18)".
while IFS= read -r -d '' f; do
  if file "$f" | grep -q 'Mach-O'; then
    archs="$(lipo -archs "$f" 2>/dev/null || true)"
    [ "$archs" = "arm64" ] || fail "$f has architectures '$archs' (expected arm64 only)"
    if otool -l "$f" | grep -q 'LC_VERSION_MIN_IPHONESIMULATOR\|platform 7'; then
      fail "$f was built for the simulator"
    fi
  elif file "$f" | grep -q 'current ar archive'; then
    fail "$f is a static archive inside the app bundle"
  fi
done < <(find "$APP" -type f -print0)

if [ -d "$APP/Frameworks" ]; then
  for fw in "$APP"/Frameworks/*.framework; do
    [ -e "$fw" ] || continue
    bin="$fw/$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$fw/Info.plist")"
    file "$bin" | grep -q 'dynamically linked shared library' || fail "$fw is not a dynamic framework"
  done
fi

# Ad-hoc signature: gives every binary a valid code-signature load command for re-signers to
# replace. Inner code first, then the app.
find "$APP" \( -name '*.dylib' -o -name '*.framework' -o -name '*.appex' \) -print0 |
  while IFS= read -r -d '' item; do codesign --force --sign - --timestamp=none "$item"; done
codesign --force --sign - --timestamp=none "$APP"
codesign --verify --strict --verbose=2 "$APP"

echo "== Packaging $OUT"
STAGE="$(mktemp -d)"
mkdir -p "$STAGE/Payload"
ditto --norsrc --noextattr "$APP" "$STAGE/Payload/$NAME.app"
rm -f "$OUT"
( cd "$STAGE" && zip -qry -X "$OLDPWD/$OUT.tmp" Payload )
mv "$OUT.tmp" "$OUT"
rm -rf "$STAGE"

# Structure check: exactly one Payload/<Name>.app with its executable and Info.plist, and none
# of the macOS zip debris (__MACOSX, ._*) that makes installers call the IPA "not a valid iOS app".
listing="$(unzip -Z1 "$OUT")"
echo "$listing" | grep -qx "Payload/$NAME.app/$EXE" || fail "IPA lacks Payload/$NAME.app/$EXE"
echo "$listing" | grep -qx "Payload/$NAME.app/Info.plist" || fail "IPA lacks Info.plist"
if echo "$listing" | grep -Eq '(^__MACOSX/|/\._)'; then fail "IPA contains macOS resource-fork debris"; fi
[ "$(echo "$listing" | grep -Ec '^Payload/[^/]+\.app/$')" -le 1 ] || fail "IPA holds more than one app"

ls -lh "$OUT"
echo "IPA OK"
