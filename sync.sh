#!/usr/bin/env bash
git remote set-url origin https://tarun225601-star:${GH_TOKEN}@github.com/tarun225601-star/Net-mesh-tarun.git
git fetch origin
git merge origin/main --allow-unrelated-histories -X ours
git push -u origin main
