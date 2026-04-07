# Document API Implementatie voortgang

## enkelvoudiginformatieobjecten

| Endpoint                     | Method | Beschrijving                                                                               | Status                  | Opmerkingen |
| ---------------------------- | ------ | ----------------------------------------------------------------------------------------- | ----------------------- | ------------ |
| /                            | GET    | Haal een lijst van enkelvoudiginformatieobjecten op op basis van de opgegeven queryparameters | $${\color{green}Done}$$ |              |
| /                            | POST   | Maak een enkelvoudiginformatieobject                                                      | $${\color{green}Done}$$ |              |
| /{UUID}/audittrail           | GET    | Haal alle audittrailrecords op voor een enkelvoudiginformatieobject                       | $${\color{green}Done}$$ |              |
| /{UUID}/audittrail/{at_UUID} | GET    | Haal een enkele audittrailrecord op op basis van enkelvoudiginformatieobject-id en audittrail-id | $${\color{green}Done}$$ |              |
| /{UUID}                      | GET    | Haal een enkel enkelvoudigInformatieObject op                                             | $${\color{green}Done}$$ |              |
| /{UUID}                      | PUT    | Werk een enkelvoudiginformatieobject volledig bij                                         | $${\color{green}Done}$$ |              |
| /{UUID}                      | PATCH  | Werk een enkelvoudiginformatieobject gedeeltelijk bij                                     | $${\color{green}Done}$$ |              |
| /{UUID}                      | DELETE | Verwijder een enkelvoudiginformatieobject                                                 | $${\color{green}Done}$$ |              |
| /{UUID}                      | HEAD   | Haal headers op voor een specifiek enkelvoudiginformatieobject                            | $${\color{green}Done}$$ |              |
| /{UUID}/download             | GET    | Download de binaire gegevens van het enkelvoudiginformatieobject                          | $${\color{green}Done}$$ |              |
| /{UUID}/lock                 | POST   | Vergrendel een enkelvoudiginformatieobject                                                | $${\color{green}Done}$$ |              |
| /{UUID}/unlock               | POST   | Ontgrendel een enkelvoudiginformatieobject                                                | $${\color{green}Done}$$ |              |
| /{UUID}/_zoek                | POST   | Zoek naar enkelvoudiginformatieobjectrecords op basis van de zoekinhoud van het verzoek   | $${\color{green}Done}$$ |              |

## gebruiksrechten

| Endpoint | Method | Beschrijving                                                        | Status                                  | Opmerkingen                         |
| -------- | ------ | ------------------------------------------------------------------ | --------------------------------------- | ----------------------------------- |
| /        | GET    | Haal een lijst van gebruiksrechten op op basis van de opgegeven queryparameters | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /        | POST   | Maak gebruiksrechten                                               | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | GET    | Haal een enkel gebruiksrechtenrecord op op basis van Id            | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | PUT    | Werk een enkel gebruiksrechtenrecord volledig bij op basis van Id  | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | PATCH  | Werk een enkel gebruiksrechtenrecord gedeeltelijk bij op basis van Id | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | DELETE | Verwijder een enkel gebruiksrechtenrecord op basis van Id          | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | HEAD   | Haal headers op voor een enkel gebruiksrechtenrecord op basis van Id | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |

## objectinformatieobject

| Endpoint | Method | Beschrijving                                                                      | Status                  | Opmerkingen |
| -------- | ------ | -------------------------------------------------------------------------------- | ----------------------- | ------------ |
| /        | GET    | Haal een lijst van objectinformatieobjectrecords op op basis van de opgegeven queryparameters | $${\color{green}Done}$$ |              |
| /        | POST   | Maak een objectinformatieobjectrelatie                                           | $${\color{green}Done}$$ |              |
| /{UUID}  | GET    | Haal een enkel objectinformatieobjectrelatie op op basis van Id                  | $${\color{green}Done}$$ |              |
| /{UUID}  | DELETE | Verwijder een enkel objectinformatieobjectrelatie op basis van Id                | $${\color{green}Done}$$ |              |
| /{UUID}  | HEAD   | Haal headers op voor een enkel objectinformatieobjectrelatie op basis van Id     | $${\color{green}Done}$$ |              |

## verzendingen

| Endpoint | Method | Beschrijving                                                    | Status                                  | Opmerkingen                         |
| -------- | ------ | -------------------------------------------------------------- | --------------------------------------- | ----------------------------------- |
| /        | GET    | Haal een lijst van verzendingen op op basis van de opgegeven queryparameters | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /        | POST   | Maak verzending                                                | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | GET    | Haal een enkele verzending op op basis van Id                  | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | PUT    | Werk een enkele verzending volledig bij op basis van Id        | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | PATCH  | Werk een enkele verzending gedeeltelijk bij op basis van Id    | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | DELETE | Verwijder een enkele verzending op op basis van Id             | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |
| /{UUID}  | HEAD   | Haal headers op voor een enkele verzending op op basis van Id  | $${\color{red}Not \space implemented}$$ | Buiten scope voor onze PoC tot nu toe |

## bestandsdelen

| Endpoint | Method | Beschrijving           | Status                                  | Opmerkingen |
| -------- | ------ | --------------------- | --------------------------------------- | ------------ |
| /{UUID}  | PUT    | Upload een bestandsdeel | $${\color{red}Not \space implemented}$$ |              |

## overig

| Functionaliteit | Status                          | Opmerkingen |
| --------------- | ------------------------------- | ------------ |
| Notificaties    | Buiten scope voor onze PoC tot nu toe |              |
| API scopes      | Buiten scope voor onze PoC tot nu toe |              |
