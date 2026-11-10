# BT Pad – telefon jako pad Xbox (np. do GeForce NOW)

Aplikacja na Androida, która zamienia telefon w **pad Bluetooth w układzie Xbox**.
Drugi telefon (np. Samsung z GeForce NOW) widzi go jak zwykły pad – nic tam nie instalujesz.

Przykład: **A34 = pad (BT Pad)**, **A32 = gra (GeForce NOW)**.

## Instalacja i pierwsze uruchomienie

1. Zainstaluj `BT Pad` na telefonie, który ma być padem. **Zamknij BT Pilot**, jeśli go masz –
   tylko jedna aplikacja naraz może udawać urządzenie Bluetooth.
2. Jeśli telefony były wcześniej sparowane (np. przez BT Pilot) – usuń to parowanie **na obu**.
3. Uruchom BT Pad → **Sparuj teraz** → **Uczyń widocznym**.
4. Na telefonie z grą: *Ustawienia → Bluetooth* → wybierz telefon-pad → Sparuj.
5. Na górze BT Pad pojawi się **● Pad połączony**.

Potem BT Pad sam łączy się z ostatnim telefonem po uruchomieniu.

## Sterowanie

- **Lewa gałka** – połóż kciuk gdziekolwiek w lewym dolnym rogu (środek gałki pojawi się pod kciukiem).
- **Prawa gałka**, **D-pad** (także po skosie), **A B X Y**, **LB RB**.
- **LT RT** – analogowe: dotknięcie = połowa siły, im niżej palec, tym mocniej.
- Szybkie stuknięcie gałki bez ruchu = wciśnięcie gałki (**L3 / R3**).
- **View**, **Menu**, **Xbox** – na środku.
- Wiele palców naraz.

## Ustawienia (⚙)

- **Czułość gałek** – ile ruchu kciuka daje pełne wychylenie.
- **LT / RT od razu z pełną siłą** – dla gier, w których spust ma działać jak zwykły przycisk.
- **Celowanie ruchem telefonu** – żyroskop steruje prawą gałką: zawsze albo tylko gdy trzymasz LT.
  Jest suwak czułości i odwracanie osi.
- **Przesuń / zmień rozmiar przycisków** – przeciągasz przyciski w nowe miejsca, − / + zmienia
  rozmiar zaznaczonego, „Domyślny układ” cofa zmiany.
- **Wstecz** trzeba nacisnąć dwa razy, żeby zamknąć pada (ochrona przed przypadkowym wyjściem).

## GeForce NOW

Uruchom grę na telefonie z GeForce NOW i naciśnij dowolny przycisk na padzie – aplikacja
przełączy się na sterowanie padem.

Sprawdzenie, czy telefon z grą widzi pad: otwórz na nim w Chrome **hardwaretester.com/gamepad**
i poruszaj gałką.

## Gdy coś nie działa

- Podczas działania w powiadomieniach widać **„BT Pad”** – to celowe: Android wyłącza pad
  Bluetooth, gdy aplikacja traci pierwszy plan (np. przy systemowym okienku), a usługa w tle temu zapobiega.
  „Zakończ” w powiadomieniu wyłącza pad.
- Napis **„Pad nie wystartował”** albo długie **„Uruchamianie…”** – stuknij go → „Spróbuj ponownie”.
  Pomaga też zamknięcie BT Pilot oraz wyłączenie i włączenie Bluetooth.

- Napis **⚠ … nie widzi pada** – stuknij go i przejdź kreator „Napraw parowanie”
  (telefon z grą pamięta stare parowanie sprzed BT Pad).
- **⚙ → Diagnostyka → Kopiuj** – dziennik połączenia do wysłania.

## Jak to działa

`BluetoothHidDevice` (Android 9+) rejestruje telefon jako pad HID z małym, standardowym
deskryptorem (83 B: 16 przycisków, D-pad, 2 gałki X/Y + Z/Rz, spusty Brake/Accelerator).
Aplikacja zapamiętuje, z którą wersją deskryptora każdy telefon był parowany, i ostrzega,
gdy trzeba sparować ponownie.

```bash
cd bt-pad
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```
