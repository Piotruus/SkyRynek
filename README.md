# Rynek 1.3.0 — hardening / Paper 1.21.4

## Build
GitHub Actions workflow: `.github/workflows/build.yml`. Build using Java 21, `mvn -B clean package`.

## Zmiany
- Zabezpieczenie przed równoległym zakupem i wypłatą przez blokady w pamięci (główny wątek Bukkit).
- Zakup zapisuje `PAYMENT_REVIEW` przed wywołaniem Vault; po udanej płatności zapisuje atomowo dostawę, należność sprzedawcy, historię i status SOLD.
- Zakupione przedmioty trafiają do trwałego schowka `/ah collect` (w dolnym GUI przycisk HOPPER), nie bezpośrednio do ekwipunku.
- Wypłaty i odbiory oznaczają stan przejściowy przed zewnętrznym efektem; niepewne przypadki są wstrzymywane zamiast automatycznego ponawiania.
- Blokada shift-click, double-click, drag w GUI.

## WAŻNE — ograniczenia
**To NIE jest gwarancja 100% bezpieczeństwa ani kod zweryfikowany na działającym serwerze.** SQLite nie może atomowo zatwierdzić operacji w zewnętrznym dostawcy Vault ani przekazania przedmiotu do ekwipunku Bukkit. Awaria w krytycznej chwili może wymagać ręcznej weryfikacji. `payment_reviews` oraz aukcje `PAYMENT_REVIEW`, wypłaty `delivered=2/3`, dostawy `status=REVIEW/CLAIMING` wymagają porównania logów, salda i stanu gracza; **nie resetuj tych statusów w ciemno**. Zrób kopię `auctions.db` przed aktualizacją.

## Przed wdrożeniem
1. Przetestuj kompilację na GitHub Actions (tu nie przeprowadzono kompilacji).
2. Na osobnym serwerze przetestuj zakup dwóch graczy naraz, pełny ekwipunek, wyjście i restart w trakcie transakcji, wygaśnięcie, wycofanie aukcji, brak Vault i dostawcę ekonomii offline.
3. Nie używaj na serwerze produkcyjnym przed audytem i testami. Zrób kopię SQLite i danych graczy.
