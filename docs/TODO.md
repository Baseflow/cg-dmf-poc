# TODO

- Add publiccode.yaml
- code of conduct
- proper copyright statements
- security.md

Release process:

- Set up automatic release notes based on GitHub PR labels. Add `.github/release.yml` to group PRs by label (breaking-change, enhancement, bug, documentation, dependencies). Update the release workflow to use `--generate-notes` instead of `--notes-file`. Labels will need to be applied consistently to PRs. See: https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes
- After the first release, bump `build.gradle.kts` version to next SNAPSHOT (e.g. `1.0.0` release → `1.1.0-SNAPSHOT` on develop). Consider automating this as a post-release step in CI.

1.6.0 changes to be implemented:

- tonenAanInitiator: boolean (default false) — whether the document may be shown to the case initiator.
- ObjectInformatieObjectExpanded / ObjectInformatieObjectEmbedded schemas — for _expand support on OIO responses.
