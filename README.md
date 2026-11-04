# Leapy

En uoffisiell Android-app for å styre en Leapmotor-bil (laget for C10) med et enklere grensesnitt enn Leapmotor-appen.

Appen snakker direkte med Leapmotors skytjeneste, på samme måte som Leapmotor-appen. Det er ingen egen server.

## Funksjoner

- Status: batteri, rekkevidde, lading, låser, dører, vinduer, temperatur, dekktrykk og posisjon
- Lås og lås opp (med fingeravtrykk eller skjermlås før opplåsing)
- Klima med temperatur og modus, hurtigvarme, hurtigkjøling og avising
- Setevarme, rattvarme, speilvarme og forvarming av batteri
- Lading: start, stopp, ladegrense og frigjøring av ladekabel
- Bagasjerom, vinduer, solskjerm, vaktmodus og «finn bilen»

## Slik installerer du

1. Last ned `leapy.apk` fra siste release.
2. Åpne filen på telefonen og tillat installasjon fra denne kilden.
3. Logg inn med Leapmotor-kontoen bilen er delt med, og legg inn bilens PIN-kode.

## Bygging

GitHub Actions bygger APK-en automatisk ved hver endring på `main` (`.github/workflows/build.yml`).

Lokalt: `./gradlew :core:test :app:assembleRelease`

## Takk og forbehold

Protokollen er hentet fra fellesskapsprosjektene
[leapmotor-api](https://github.com/markoceri/leapmotor-api) (AGPL-3.0),
[leapmotor-certs](https://github.com/markoceri/leapmotor-certs) og
[leapmotor-mate](https://github.com/ProtossBlaster/leapmotor-mate) (AGPL-3.0, Leapmotors sub-CA for serververifisering).
Siden koden bygger på AGPL-lisensiert kode, er dette prosjektet også AGPL-3.0.

Leapy er ikke laget av eller godkjent av Leapmotor. Leapmotor kan endre tjenesten når som helst. Bruk på eget ansvar.
