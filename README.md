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
   - **Nastavitve IP** (možnost prilagoditve IP naslovov postaj, shranjeno v `%LOCALAPPDATA%\VzpenjacaKontrola\settings.ini`)
   - **Tiha namestitev prek ukazne vrstice:** `VzpenjacaKontrola-Namestitev.exe /S`
