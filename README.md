# Nadzorna plošča vzpenjače (`VzpenjacaKontrola`)

Namenska namizna aplikacija za sistem Windows z lastnimi prilagojenimi kontrolami za upravljanje obeh postaj vzpenjače:
- **Zgornja postaja (Zgoraj):** `http://10.12.220.34/kontrola/control.htm`
- **Spodnja postaja (Spodaj):** `http://10.12.220.36/kontrola/control.htm`

## Datoteke v projektu

- [`VzpenjacaKontrola-Namestitev.exe`](./VzpenjacaKontrola-Namestitev.exe) — **Uporabniški namestitveni program (brez skrbniških / Administrator pravic)**. Namesti aplikacijo v `%LOCALAPPDATA%\Programs\VzpenjacaKontrola`, ustvari bližnjici na Namizju in v meniju Start ter doda možnost odstranitve v nastavitve sistema Windows (*Nameščene aplikacije*).
- [`VzpenjacaKontrola.exe`](./VzpenjacaKontrola.exe) — **Samostojna prenosna (portable) izvedljiva datoteka**, ki jo lahko zaženete neposredno brez namestitve.
- [`Program.cs`](./Program.cs) — Izvorna koda glavne aplikacije, komunikacijskega odjemalca (`FunicularClient`) in prilagojenih grafičnih kontrol (`OperatorButton`, `TimerDisplayControl`, `PersonsDisplayControl`, `StationControlPanel`).
- [`Installer.cs`](./Installer.cs) — Izvorna koda namestitvenega in odstranitvenega programa brez zahteve za UAC / skrbniške pravice.
- [`app.manifest`](./app.manifest) — Win32 manifest z nastavitvijo `requestedExecutionLevel level="asInvoker"`, ki zagotavlja delovanje in namestitev brez skrbniških pravic.
- [`build.ps1`](./build.ps1) — Skripta za prevajanje ikone, aplikacije in namestitvenega paketa z vgrajenim prevajalnikom .NET Framework (`csc.exe`).

## Funkcionalnosti

1. **Vzporedni prikaz obeh postaj (Zgoraj `.34` in Spodaj `.36`) v enem oknu:**
   - Digitalni prikaz odštevalnika časa (`MM:SS.d`) s tekočim 100 ms odštevanjem in samodejno sinhronizacijo s krmilnikom vsaki 2 sekundi.
   - Prikaz nastavljenega časa (`NASTAVLJENI ČAS`) in grafični prikaz preostalega časa.
   - Digitalni števec oseb (`TRENUTNO / MAX`) z barvnim opozorilom ob polni kapaciteti.
2. **Prilagojene operaterske kontrole za vsako postajo:**
   - **Povečanje časa:** `+ 15 sek` (`?1`), `+ 1 min` (`?2`), `+ 5 min` (`?3`)
   - **Zmanjšanje časa:** `- 15 sek` (`?4`), `- 1 min` (`?5`), `- 5 min` (`?6`)
   - **Upravljanje potnikov:** `+ 1 oseba` (`?7`), `- 1 oseba` (`?8`), `Ponastavi Zgoraj` / `Ponastavi Spodaj` (`?9`)
3. **Zanesljiva neposredna komunikacija:**
   - Uporablja neposredno TCP/HTTP komunikacijo s krmilnikom na vratih `80`, kar odpravi težave z nestandardnimi glavami odgovorov (`HTTP/0.9` oz. `HTTP/1.1 200 OK` brez glav) v sodobnih brskalnikih.
4. **Dodatne možnosti v orodni vrstici:**
   - **Vedno na vrhu** (*Always on Top*)
   - **Potrdi ponastavitev oseb** (izbirna zaščita pred nenamernim klikom na ponastavitev)
   - **Mobilni dostop (Oblak)...** — vgrajen odhodni HTTPS posrednik (`CloudRelayBridge`), ki brez odpiranja vrat na službenem požarnem zidu in brez skrbniških pravic poveže PC aplikacijo z mobilno Android aplikacijo prek varnega kanala (privzeto `vzpenjaca-3436`).
   - **Tiha namestitev prek ukazne vrstice:** `VzpenjacaKontrola-Namestitev.exe /S`

## Mobilna aplikacija za Android (`AndroidApp/`)

V mapi [`AndroidApp/`](./AndroidApp) je izvorna koda namenske Android aplikacije (**Vzpenjača**), ki omogoča spremljanje in upravljanje obeh postaj od koderkoli (prek 4G/5G ali domačega Wi-Fi omrežja):

- [`AndroidApp/app/src/main/java/si/vzpenjaca/kontrola/MainActivity.kt`](./AndroidApp/app/src/main/java/si/vzpenjaca/kontrola/MainActivity.kt) — Celotna Android aplikacija z:
  - **Oblačnim mostom (`CloudRelay`)**: V realnem času sprejema telemetrijo obeh postaj iz PC aplikacije [`VzpenjacaKontrola.exe`](./VzpenjacaKontrola.exe) in pošilja operaterske ukaze (`10 min`, `6 min`, `+1 min`, `-1 min`, `+5 min`, `-5 min`, `+1 oseba`, `-1 oseba`, `Ponastavi števec`).
  - **Neposrednim LAN načinom (`DirectLanFunicularClient`)**: Možnost preklopa v nastavitvah na neposredno surovo TCP komunikacijo s krmilnikoma `10.12.220.34` in `10.12.220.36`, kadar je telefon povezan na službeni Wi-Fi ali VPN.
  - **Enakim temnim vmesnikom in sintetiziranimi zvočnimi opozorili (`MobileChimePlayer`)** kot namizna aplikacija.
