# TODO
- Add publiccode.yaml
- PoC uit de naam halen
- auth-jwt zou eigenlijk ook een rol moeten hebben.
- set a description for the github repo

Future development:
- bestands endpoints: het doel hiervan is om de upload en download van bestanden lost te koppelen van de concerns van de metadata registratie.
- Zie ook https://github.com/VNG-Realisatie/gemma-zaken/issues/2565
- register validatie van objectinformatieobjecten
- vertalingen voor admin portal
- Implement freshly released 1.6.0 and 1.7.0 changes:
  - tonenAanInitiator: boolean (default false) — whether the document may be shown to the case initiator.
  - ObjectInformatieObjectExpanded / ObjectInformatieObjectEmbedded schemas — for _expand support on OIO responses.
- Officially release helm charts

Development stack:
- Voeg OpenZaak toe aan de docker-compose-devstack zodat `informatieobjecttype`-validatie lokaal getest kan worden zonder een externe instantie.
- Voeg Collabora Online toe aan de docker-compose-devstack voor lokaal testen van de WOPI-integratie.

Release process:
- Set up automatic release notes based on GitHub PR labels. Add `.github/release.yml` to group PRs by label (breaking-change, enhancement, bug, documentation, dependencies). Update the release workflow to use `--generate-notes` instead of `--notes-file`. Labels will need to be applied consistently to PRs. See: https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes
- After the first release, bump `build.gradle.kts` version to next SNAPSHOT (e.g. `1.0.0` release → `1.1.0-SNAPSHOT` on develop). Consider automating this as a post-release step in CI.

Plannen voor GZAC:
- maak het mogelijk voor de Delete objectinformatieobject action to make use of the new bulk delete api operation in the DRC APIs. This way you can delete by relation identifier, instead of by objectinformatieobject identifier.
- maak het mogelijk om vanuit IKO uploads te ondersteunen, die direct aan een relatie gekoppeld worden.
- configureer zoek en filter mogelijkheden voor IKO uploads
