#!/bin/bash

# release rbenv-crate crate

if [[ $# -lt 3 ]]; then
  echo "usage: $(basename $0) previous-version new-version next-version" >&2
  exit 1
fi

previous_version=$1
version=$2
next_version=$3

echo ""
echo "Start release of $version, previous version is $previous_version"
echo ""
echo ""

lein do clean, with-profile +no-checkouts test && \
git flow release start $version || exit 1

lein with-profile +release set-version ${version} :previous-version ${previous_version} \
  || { echo "set version failed" >2 ; exit 1; }

echo ""
echo ""
echo "Changes since $previous_version"
git --no-pager log --pretty=changelog $previous_version..
echo ""
echo ""

$EDITOR resources/pallet_crate/rbenv_crate/meta.edn
$EDITOR doc-src/USAGE.md
lein crate-doc || exit 1
$EDITOR README.md
git add \
    resources/pallet_crate/rbenv_crate/meta.edn \
    doc-src/USAGE.md \
    doc-src/FOOTER.md \
    README.md \
&& if ! [ 0 == $(git status --porcelain 2>/dev/null| grep "^M" | wc -l) ]; then
     git commit -m "Updated metadata and usage for $version"
   fi \
|| exit 1


echo "Now edit project.clj, and ReleaseNotes"

$EDITOR project.clj
$EDITOR ReleaseNotes.md

echo -n "commiting project.clj, and release notes.  enter to continue:" \
&& read x \
&& git add project.clj ReleaseNotes.md \
&& git commit -m "Updated project.clj, release notes and readme for $version" \
&& echo -n "Peform release.  enter to continue:" && read x \
&& lein do clean, install, with-profile +no-checkouts test, deploy clojars \
&& git flow release finish $version \
&& echo "Now push to github. Don't forget the tags!" \
&& lein with-profile +doc doc \
&& lein with-profile +release set-version ${next_version} \
&& git add project.clj \
&& git commit -m "Updated version for next release cycle"
