# Aplicație Calculator

Un calculator modern dezvoltat în JavaFX cu funcționalități avansate, teme multiple, conversie valutară și istoric persistent al calculelor.

## Funcționalități

- **Operații Calculator de Bază**: Adunare, scădere, înmulțire, împărțire
- **Funcții Științifice**: Rădăcină pătrată, calcule cu procente
- **Teme Multiple**: 7 teme de culori distincte (Dark, Light, Barbie, Neon, Sunset, Forest, Cyberpunk)
- **Convertor Valutar**: Cursuri de schimb în timp real cu integrare API live
- **Istoric Calcule**: Stocare persistentă a calculelor anterioare
- **UI Modern**: Interfață curată cu meniu hamburger și opțiuni scrollabile
- **Auto-conversie**: Conversie directă a rezultatelor calculatorului în diferite valute
- **Suport Tastatură**: Suport complet pentru introducerea de la tastatură

## Tehnologii Utilizate

- **JavaFX 24.0.2**: Framework GUI pentru aplicații desktop moderne
- **Java HTTP Client**: Pentru obținerea cursurilor de schimb în timp real
- **File I/O**: Stocare persistentă pentru istoricul calculelor
- **REST API Integration**: Cursuri de schimb în timp real de la exchangerate-api.com

## Cerințe

- Java Development Kit (JDK) 11 sau mai nou
- JavaFX SDK 24.0.2

## Rularea Aplicației

### Compilare
```bash
javac --module-path "D:\Facultate\app\javafx-sdk-24.0.2\lib" --add-modules javafx.controls,javafx.fxml -d bin src/*.java
```

### Execuție
```bash
java --module-path "D:\Facultate\app\javafx-sdk-24.0.2\lib" --add-modules javafx.controls,javafx.fxml -cp bin CalculatorApp
```

## Structura Proiectului

```
src/
├── CalculatorApp.java     # Fișierul principal al aplicației
├── CalculatorLogic.java   # Logica calculatorului și operațiile
└── calculator_history.txt # Istoricul persistent al calculelor
```

## Utilizare

1. Lansează aplicația folosind comanda de rulare de mai sus
2. Folosește calculatorul pentru operații matematice de bază și avansate
3. Accesează temele prin meniul hamburger din colțul stânga-sus
4. Comută în modul convertor valutar pentru calcule cu cursuri de schimb în timp real
5. Vizualizează istoricul calculelor prin opțiunile din meniu

## Funcționalități Avansate
- **Sistem de Teme**: Teme cu coduri de culori și selecție prin cercuri
- **Istoric Persistent**: Stocare automată a ultimelor 100 de calcule cu scrollbar
- **Validare Input**: Parsare inteligentă a input-ului și prevenirea erorilor
- **Design Responsiv**: UI-ul se adaptează la schimbările de temă cu stil consistent
- **Meniu Scrollabil**: Meniu hamburger cu scroll fluid pentru toate opțiunile

## Licență

Acest proiect este dezvoltat în scop educativ.
