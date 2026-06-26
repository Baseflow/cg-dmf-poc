# Objectkoppelingen — objectgericht werken

CG-DMF breidt de standaard VNG Documenten API 1.5.0 uit met de mogelijkheid om documenten te koppelen aan **willekeurige objecten** — niet alleen aan Zaken of Besluiten. Dit maakt het mogelijk om documenten objectgericht te ontsluiten, zonder dat ze per se via een zaak bereikbaar hoeven te zijn.

## Achtergrond

In de standaard Common Ground-architectuur zijn documenten altijd via een Zaak te benaderen. Voor use cases waarbij documenten primair worden beheerd en ontsloten vanuit het oogpunt van een fysiek object — en niet vanuit een proces of zaak — is dit onpraktisch. Denk aan situaties waarbij:

- één document betrekking heeft op meerdere objecten tegelijk
- een object tienduizenden documenten heeft verspreid over duizenden zaken
- documenten bestaan van vóór het tijdperk van zaakgericht werken

CG-DMF lost dit op door naast de standaard `zaak`- en `besluit`-relaties ook andere objecttypes toe te staan in `ObjectInformatieObject`-relaties (via API-versie `1.5.0-baseflow`).

## Hoe een objectkoppeling werkt

Een objectkoppeling (`ObjectInformatieObject`) legt een relatie vast tussen:
- een `informatieobject` — de URL van een document in de DMF
- een `object` — de URL van een extern object (bijv. een item uit een objectenregistratie)
- een `objectType` — een logische aanduiding van het register waartoe het object behoort

Een document kan tegelijkertijd via een zaak én via een objectkoppeling vindbaar zijn. De twee relaties staan onafhankelijk van elkaar in de DMF.

## Vereisten voor `objectType`

Het veld `objectType` moet voldoen aan het patroon `^[a-z0-9]+(-[a-z0-9]+)*$`:
- alleen kleine letters, cijfers en koppeltekens
- mag niet beginnen of eindigen met een koppelteken
- geen spaties of andere tekens

De standaard VNG-types zijn `zaak` en `besluit`. Elk ander geldig type kan worden gebruikt voor objectgericht werken. De DMF valideert het type niet tegen een extern register — de waarde is een afspraak tussen de systemen die de koppeling aanmaken en raadplegen.

| Waarde       | Beschrijving                                           |
|--------------|--------------------------------------------------------|
| `zaak`       | Standaard VNG-type: koppeling aan een zaak (OpenZaak)  |
| `besluit`    | Standaard VNG-type: koppeling aan een besluit          |
| `object`     | Object uit de VNG Objecten API                         |

Zie [docs/implementatie.md](implementatie.md) voor de volledige lijst van beschikbare API-parameters, waaronder filters op `objectType` en `object`-URL.

## Vereisten voor de object-URL

De `object`-waarde is een **unieke URL** die verwijst naar één specifiek object:
- De URL moet stabiel zijn — als het object verplaatst wordt, raken objectkoppelingen los.
- Gebruik bij voorkeur een URL die door een API wordt bediend.
- Meerdere objectkoppelingen voor hetzelfde object zijn mogelijk (bijv. meerdere documenten aan hetzelfde object gekoppeld).

## Aanmaken via GZAC

Objectkoppelingen worden aangemaakt via de plugin-actie **Koppel document met object** in de Documenten API-plugin van GZAC. Deze actie is alleen beschikbaar wanneer de plugin is geconfigureerd op API-versie `1.5.0-baseflow`.

Parameters:
- **Object URL** — de URL van het externe object
- **Objecttype** — het type van het object (bijv. een zelfgekozen register-aanduiding)

De URL van de aangemaakte koppeling wordt teruggeschreven als process variable `pv:objectInformatieObjectUrl` en kan later worden gebruikt om de koppeling te verwijderen via de actie **Document koppeling verwijderen**.

Zie [docs/handleiding.md](handleiding.md) voor de volledige GZAC-configuratie.

## Raadplegen

Afnemers kunnen documenten opvragen gefilterd op object-URL en objecttype:

```
GET /documenten/api/v1/enkelvoudiginformatieobjecten
    ?objectinformatieobjecten__object=<object-url>
    &objectinformatieobjecten__objectType=<objecttype>
```

Dit zijn experimentele filterparameters bovenop de VNG-standaard. Authenticatie verloopt via ZGW client credentials.

## Levenscyclus

Objectkoppelingen worden **zelfstandig bijgehouden** in de DMF en zijn niet automatisch gekoppeld aan de levenscyclus van een zaak. Als een zaak wordt gesloten of verwijderd, blijven bijbehorende objectkoppelingen bestaan. Het verwijderen van een objectkoppeling moet actief worden gedaan via de DMF API of via de GZAC-plugin-actie **Document koppeling verwijderen**.