#!/usr/bin/env bash
# Publishes the Maven artifacts to the token-free static repository on GitHub Pages:
#   https://hariobudiharjo.github.io/pantau-http-kmm/maven
# Usage: JAVA_HOME=<jdk17> scripts/publish-pages.sh
set -euo pipefail
cd "$(dirname "$0")/.."

REPO_URL=$(git remote get-url origin)
STAGING="build/maven-repo"
WORKTREE="build/gh-pages"

rm -rf "$STAGING"
./gradlew publishAllPublicationsToPagesRepository

rm -rf "$WORKTREE"
git fetch origin gh-pages 2>/dev/null || true
if git show-ref --verify --quiet refs/remotes/origin/gh-pages; then
  git worktree add "$WORKTREE" gh-pages
else
  git worktree add --detach "$WORKTREE"
  (cd "$WORKTREE" && git checkout --orphan gh-pages && git rm -rf -q . 2>/dev/null || true)
fi

mkdir -p "$WORKTREE/maven"
rsync -a "$STAGING/" "$WORKTREE/maven/"
touch "$WORKTREE/.nojekyll"
cat > "$WORKTREE/index.html" <<'HTML'
<!doctype html><meta charset="utf-8"><title>PantauHTTP Maven repository</title>
<h1>PantauHTTP Maven repository</h1>
<p>Add <code>maven("https://hariobudiharjo.github.io/pantau-http-kmm/maven")</code> to your Gradle repositories, then depend on
<code>com.pantauhttp:pantau-http</code> or <code>com.pantauhttp:pantau-http-core</code>. Source: <a href="https://github.com/HarioBudiharjo/pantau-http-kmm">github.com/HarioBudiharjo/pantau-http-kmm</a>.</p>
HTML

VERSION=$(grep -E '^pantau = ' gradle/libs.versions.toml | sed -E 's/.*"([^"]+)".*/\1/')
(
  cd "$WORKTREE"
  git add -A
  if git diff --cached --quiet; then echo "gh-pages already up to date"; else
    git commit -q -m "maven: publish $VERSION"
    git push -q "$REPO_URL" HEAD:gh-pages
    echo "pushed gh-pages with $VERSION"
  fi
)
git worktree remove --force "$WORKTREE"
