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

## GeForce NOW

Uruchom grę na telefonie z GeForce NOW i naciśnij dowolny przycisk na padzie – aplikacja
przełączy się na sterowanie padem.

Sprawdzenie, czy telefon z grą widzi pad: otwórz na nim w Chrome **hardwaretester.com/gamepad**
i poruszaj gałką.

## Gdy coś nie działa

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
