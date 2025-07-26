
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDate;

public class CalculatorApp extends Application {
    private CalculatorLogic logic = new CalculatorLogic();
    private TextField display = new TextField();
    private boolean isEmptyInput = true;
    // ...existing code...
    private boolean justCalculated = false;
    private boolean lastWasEqual = false;
    private String currentInput = "";
    
    // Istoric și teme
    private java.util.List<String> history = new java.util.ArrayList<>();
    private String currentTheme = "dark";
    private VBox historyOverlay;
    private BorderPane mainRoot;
    private GridPane buttonGrid;
    private Button lastOperatorButton = null;
    
    // Currency conversion
    private java.util.Map<String, Double> exchangeRates = new java.util.HashMap<>();
    private String selectedFromCurrency = "RON";
    private String selectedToCurrency = "EUR";
    private Label conversionResult;
    private TextField customAmountField;
    private ComboBox<String> fromCurrencyCombo;
    private ComboBox<String> toCurrencyCombo;
    private Button refreshRatesBtn;
    private Label ratesUpdateLabel;

    @Override
    public void start(Stage primaryStage) {
        display.setFont(Font.font("Segoe UI", 72));
        display.setAlignment(Pos.CENTER_RIGHT);
        display.setEditable(false);
        display.setPrefHeight(90);
        display.setStyle(getDisplayStyle());
        display.setText("0");
        display.positionCaret(0);
        display.setFocusTraversable(false);
        display.setOpacity(1.0);

        // Buton hamburger menu
        Button menuButton = createMenuButton();
        
        // Header cu display și menu
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_RIGHT);
        header.setPadding(new Insets(10, 20, 0, 20));
        header.getChildren().addAll(menuButton);
        
        buttonGrid = createButtons();
        buttonGrid.setAlignment(Pos.CENTER);
        buttonGrid.setMaxWidth(360);

        VBox mainPanel = new VBox(18, header, display, buttonGrid);
        mainPanel.setAlignment(Pos.CENTER);
        mainPanel.setPadding(new Insets(0));
        mainPanel.setStyle("-fx-background-color: transparent;");

        Label copyright = new Label("© Razvan Drutu - Modern Calculator");
        copyright.setFont(Font.font("Segoe UI", 13));
        copyright.setTextFill(Color.web(getThemeColor("copyright")));
        copyright.setAlignment(Pos.CENTER);
        copyright.setPadding(new Insets(8));

        mainRoot = new BorderPane();
        mainRoot.setCenter(mainPanel);
        mainRoot.setBottom(copyright);
        BorderPane.setAlignment(mainPanel, Pos.CENTER);
        BorderPane.setAlignment(copyright, Pos.CENTER);
        mainRoot.setStyle(getBackgroundStyle());

        // Overlay pentru istoric (inițial ascuns)
        historyOverlay = createHistoryOverlay();
        historyOverlay.setVisible(false);

        StackPane root = new StackPane();
        root.getChildren().addAll(mainRoot, historyOverlay);

        Scene scene = new Scene(root, 420, 620);
        primaryStage.setTitle("Modern Calculator");
        primaryStage.setScene(scene);
        primaryStage.show();

        setupKeyboardSupport(scene);
        
        // Initialize exchange rates with live API
        initializeExchangeRates();
        
        // Load persistent history
        loadHistory();
    }
    
    private void loadHistory() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("calculator_history.txt"));
            String line;
            while ((line = reader.readLine()) != null && history.size() < 100) {
                history.add(line);
            }
            reader.close();
        } catch (Exception e) {
            // No history file yet, that's fine
        }
    }
    
    private void saveHistoryToFile() {
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter("calculator_history.txt"));
            for (String calculation : history) {
                writer.println(calculation);
            }
            writer.close();
        } catch (Exception e) {
            System.err.println("Could not save history: " + e.getMessage());
        }
    }

    private void clearHistory() {
        history.clear();
        saveHistoryToFile();
    }

    private void initializeExchangeRates() {
        // Set default rates (will be updated from API)
        exchangeRates.put("EUR", 1.0);
        exchangeRates.put("USD", 1.09);
        exchangeRates.put("RON", 4.97);
        exchangeRates.put("GBP", 0.85);
        exchangeRates.put("TRY", 37.15);
        
        // Load rates from API in background
        loadLiveExchangeRates();
    }
    
    private void loadLiveExchangeRates() {
        Task<Void> loadRatesTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Using free API from exchangerate-api.com
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.exchangerate-api.com/v4/latest/EUR"))
                        .GET()
                        .build();
                    
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200) {
                        parseExchangeRates(response.body());
                        updateStatusMessage("Rate live: " + LocalDate.now());
                    } else {
                        updateStatusMessage("Eroare API - rate offline");
                    }
                } catch (Exception e) {
                    updateStatusMessage("Offline - rate din cache");
                    System.err.println("Exchange rate API error: " + e.getMessage());
                }
                return null;
            }
        };
        
        Thread apiThread = new Thread(loadRatesTask);
        apiThread.setDaemon(true);
        apiThread.start();
    }
    
    private void parseExchangeRates(String jsonResponse) {
        try {
            // Simple JSON parsing pentru rate-uri
            if (jsonResponse.contains("\"rates\"")) {
                String ratesSection = jsonResponse.substring(jsonResponse.indexOf("\"rates\""));
                
                // Parse USD
                if (ratesSection.contains("\"USD\"")) {
                    String usdValue = extractRate(ratesSection, "USD");
                    if (usdValue != null) exchangeRates.put("USD", Double.parseDouble(usdValue));
                }
                
                // Parse RON
                if (ratesSection.contains("\"RON\"")) {
                    String ronValue = extractRate(ratesSection, "RON");
                    if (ronValue != null) exchangeRates.put("RON", Double.parseDouble(ronValue));
                }
                
                // Parse GBP
                if (ratesSection.contains("\"GBP\"")) {
                    String gbpValue = extractRate(ratesSection, "GBP");
                    if (gbpValue != null) exchangeRates.put("GBP", Double.parseDouble(gbpValue));
                }
                
                // Parse TRY
                if (ratesSection.contains("\"TRY\"")) {
                    String tryValue = extractRate(ratesSection, "TRY");
                    if (tryValue != null) exchangeRates.put("TRY", Double.parseDouble(tryValue));
                }
                
                // Actualizează UI cu ratele noi
                javafx.application.Platform.runLater(() -> {
                    if (conversionResult != null && customAmountField != null && !customAmountField.getText().trim().isEmpty()) {
                        convertCurrentResult();
                    }
                });
                
                System.out.println("Exchange rates updated successfully!");
            }
        } catch (Exception e) {
            System.err.println("Error parsing exchange rates: " + e.getMessage());
        }
    }
    
    private String extractRate(String json, String currency) {
        try {
            String pattern = "\"" + currency + "\":";
            int startIndex = json.indexOf(pattern);
            if (startIndex == -1) return null;
            
            startIndex += pattern.length();
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
            
            return json.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            return null;
        }
    }
    
    private void updateStatusMessage(String message) {
        javafx.application.Platform.runLater(() -> {
            if (ratesUpdateLabel != null) {
                ratesUpdateLabel.setText(message);
            }
        });
    }
    
    private void saveHistory() {
        // Keep only last 100 calculations
        if (history.size() > 100) {
            history.remove(0);
        }
        saveHistoryToFile();
    }
    
    private void convertCurrentResult() {
        // Check if conversionResult is initialized
        if (conversionResult == null) {
            return; // Skip conversion if not initialized yet
        }
        
        try {
            String amountText;
            if (customAmountField != null && !customAmountField.getText().trim().isEmpty()) {
                amountText = customAmountField.getText().trim();
            } else {
                amountText = display.getText().trim();
            }
            
            if (amountText.equals("0") || amountText.isEmpty()) {
                conversionResult.setText("💡 Introdu o sumă sau fă un calcul pentru conversie");
                return;
            }
            
            double amount = Double.parseDouble(amountText);
            double converted = convertCurrency(amount, selectedFromCurrency, selectedToCurrency);
            
            String formattedResult = String.format("%.2f %s = %.2f %s", 
                amount, selectedFromCurrency, converted, selectedToCurrency);
            conversionResult.setText("💰 " + formattedResult);
            
        } catch (NumberFormatException e) {
            conversionResult.setText("❌ Sumă invalidă pentru conversie");
        } catch (Exception e) {
            conversionResult.setText("⚠️ Eroare la conversie: " + e.getMessage());
        }
    }
    
    private VBox createCurrencyConverter() {
        VBox converterBox = new VBox(15);
        converterBox.setAlignment(Pos.CENTER);
        converterBox.setPadding(new Insets(15));
        converterBox.setStyle(String.format("-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 12; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 12;", getThemeColor("button_operator")));
        
        // Amount input section
        VBox amountSection = new VBox(8);
        amountSection.setAlignment(Pos.CENTER);
        
        Label amountLabel = new Label("💰 Sumă de convertit");
        amountLabel.setTextFill(Color.WHITE);
        amountLabel.setFont(Font.font("Segoe UI", 14));
        amountLabel.setStyle("-fx-font-weight: bold;");
        
        HBox amountInput = new HBox(8);
        amountInput.setAlignment(Pos.CENTER);
        
        customAmountField = new TextField();
        customAmountField.setPromptText("Introdu suma...");
        customAmountField.setPrefWidth(120);
        customAmountField.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 8; -fx-padding: 8; -fx-font-size: 13px;", getThemeColor("button_normal"), getThemeColor("button_text")));
        
        // Auto-convert as user types
        customAmountField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty() && newText.matches("\\d*\\.?\\d*")) {
                convertCurrentResult();
            }
        });
        
        Button useResultBtn = new Button("📱 Din Calcul");
        useResultBtn.setFont(Font.font("Segoe UI", 11));
        useResultBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 8; -fx-padding: 6 12;", getThemeColor("button_operator"), getThemeColor("button_text_operator")));
        useResultBtn.setOnAction(e -> {
            customAmountField.setText(display.getText());
            convertCurrentResult();
        });
        
        amountInput.getChildren().addAll(customAmountField, useResultBtn);
        amountSection.getChildren().addAll(amountLabel, amountInput);
        
        // Currency selection section
        VBox currencySection = new VBox(8);
        currencySection.setAlignment(Pos.CENTER);
        
        Label currencyLabel = new Label("🔄 Conversie");
        currencyLabel.setTextFill(Color.WHITE);
        currencyLabel.setFont(Font.font("Segoe UI", 14));
        currencyLabel.setStyle("-fx-font-weight: bold;");
        
        HBox currencySelection = new HBox(10);
        currencySelection.setAlignment(Pos.CENTER);
        
        // From currency
        VBox fromBox = new VBox(5);
        fromBox.setAlignment(Pos.CENTER);
        Label fromLabel = new Label("Din");
        fromLabel.setTextFill(Color.LIGHTGRAY);
        fromLabel.setFont(Font.font("Segoe UI", 11));
        
        fromCurrencyCombo = new ComboBox<>();
        fromCurrencyCombo.getItems().addAll("EUR", "USD", "RON", "GBP", "TRY");
        fromCurrencyCombo.setValue(selectedFromCurrency);
        fromCurrencyCombo.setPrefWidth(70);
        fromCurrencyCombo.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 6;", getThemeColor("button_normal"), getThemeColor("button_text")));
        fromCurrencyCombo.setOnAction(e -> {
            selectedFromCurrency = fromCurrencyCombo.getValue();
            convertCurrentResult();
        });
        
        fromBox.getChildren().addAll(fromLabel, fromCurrencyCombo);
        
        // Swap button
        Button swapBtn = new Button("⇄");
        swapBtn.setFont(Font.font("Segoe UI", 16));
        swapBtn.setPrefSize(35, 35);
        swapBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 18;", getThemeColor("button_clear"), getThemeColor("button_text_operator")));
        swapBtn.setOnAction(e -> {
            String temp = selectedFromCurrency;
            selectedFromCurrency = selectedToCurrency;
            selectedToCurrency = temp;
            fromCurrencyCombo.setValue(selectedFromCurrency);
            toCurrencyCombo.setValue(selectedToCurrency);
            convertCurrentResult();
        });
        
        // To currency
        VBox toBox = new VBox(5);
        toBox.setAlignment(Pos.CENTER);
        Label toLabel = new Label("În");
        toLabel.setTextFill(Color.LIGHTGRAY);
        toLabel.setFont(Font.font("Segoe UI", 11));
        
        toCurrencyCombo = new ComboBox<>();
        toCurrencyCombo.getItems().addAll("EUR", "USD", "RON", "GBP", "TRY");
        toCurrencyCombo.setValue(selectedToCurrency);
        toCurrencyCombo.setPrefWidth(70);
        toCurrencyCombo.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 6;", getThemeColor("button_normal"), getThemeColor("button_text")));
        toCurrencyCombo.setOnAction(e -> {
            selectedToCurrency = toCurrencyCombo.getValue();
            convertCurrentResult();
        });
        
        toBox.getChildren().addAll(toLabel, toCurrencyCombo);
        
        currencySelection.getChildren().addAll(fromBox, swapBtn, toBox);
        currencySection.getChildren().addAll(currencyLabel, currencySelection);
        
        // Result section
        VBox resultSection = new VBox(8);
        resultSection.setAlignment(Pos.CENTER);
        
        conversionResult = new Label("🔢 Fă un calcul sau introdu o sumă personalizată");
        conversionResult.setTextFill(Color.WHITE);
        conversionResult.setFont(Font.font("Segoe UI", 12));
        conversionResult.setAlignment(Pos.CENTER);
        conversionResult.setWrapText(true);
        conversionResult.setMaxWidth(250);
        conversionResult.setStyle(String.format("-fx-background-color: rgba(0,0,0,0.4); -fx-background-radius: 8; -fx-padding: 12; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 8;", getThemeColor("button_operator")));
        
        // Refresh button and status
        HBox refreshSection = new HBox(8);
        refreshSection.setAlignment(Pos.CENTER);
        
        refreshRatesBtn = new Button("🔄 Actualizează");
        refreshRatesBtn.setFont(Font.font("Segoe UI", 10));
        refreshRatesBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 6; -fx-padding: 4 8;", getThemeColor("button_clear"), getThemeColor("button_text_operator")));
        refreshRatesBtn.setOnAction(e -> {
            refreshRatesBtn.setText("⏳ Actualizez...");
            refreshRatesBtn.setDisable(true);
            loadLiveExchangeRates();
            
            // Re-enable button after 3 seconds
            Task<Void> enableTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    Thread.sleep(3000);
                    javafx.application.Platform.runLater(() -> {
                        refreshRatesBtn.setText("🔄 Actualizează");
                        refreshRatesBtn.setDisable(false);
                    });
                    return null;
                }
            };
            new Thread(enableTask).start();
        });
        
        ratesUpdateLabel = new Label("Încărcare rate...");
        ratesUpdateLabel.setTextFill(Color.web(getThemeColor("update_text")));
        ratesUpdateLabel.setFont(Font.font("Segoe UI", 9));
        
        refreshSection.getChildren().addAll(refreshRatesBtn, ratesUpdateLabel);
        resultSection.getChildren().addAll(conversionResult, refreshSection);
        
        converterBox.getChildren().addAll(amountSection, currencySection, resultSection);
        
        // Initialize conversion display
        convertCurrentResult();
        
        return converterBox;
    }
    
    private double convertCurrency(double amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        
        // Check if exchange rates are loaded
        if (exchangeRates.isEmpty() || !exchangeRates.containsKey(fromCurrency) || !exchangeRates.containsKey(toCurrency)) {
            throw new RuntimeException("Rate de schimb nu sunt încărcate");
        }
        
        // Convert to EUR first, then to target currency
        double inEUR = amount / exchangeRates.get(fromCurrency);
        return inEUR * exchangeRates.get(toCurrency);
    }

    // Metode pentru teme
    private String getThemeColor(String element) {
        switch (currentTheme) {
            case "light":
                switch (element) {
                    case "background": return "#f8f9fa";
                    case "display_bg": return "#ffffff";
                    case "display_text": return "#2c3e50";
                    case "button_normal": return "#e9ecef";
                    case "button_operator": return "#007bff";
                    case "button_clear": return "#dc3545";
                    case "button_text": return "#2c3e50";
                    case "button_text_operator": return "#ffffff";
                    case "copyright": return "#6c757d";
                    case "update_text": return "#495057";
                    case "rates_text": return "#343a40";
                    case "input_box_bg": return "rgba(108,117,125,0.1)";
                    default: return "#2c3e50";
                }
            case "barbie":
                switch (element) {
                    case "background": return "#ff69b4";
                    case "display_bg": return "#ffb6c1";
                    case "display_text": return "#8b008b";
                    case "button_normal": return "#ffc0cb";
                    case "button_operator": return "#ff1493";
                    case "button_clear": return "#dc143c";
                    case "button_text": return "#8b008b";
                    case "button_text_operator": return "#ffffff";
                    case "copyright": return "#8b008b";
                    default: return "#8b008b";
                }
            case "neon":
                switch (element) {
                    case "background": return "#0a0a0a";
                    case "display_bg": return "#1a1a1a";
                    case "display_text": return "#00ff41";
                    case "button_normal": return "#1a1a1a";
                    case "button_operator": return "#ff0080";
                    case "button_clear": return "#ff4500";
                    case "button_text": return "#00ff41";
                    case "button_text_operator": return "#000000";
                    case "copyright": return "#00ff41";
                    default: return "#00ff41";
                }
            case "sunset":
                switch (element) {
                    case "background": return "#ff6b6b";
                    case "display_bg": return "#ff8e53";
                    case "display_text": return "#ffffff";
                    case "button_normal": return "#ff7675";
                    case "button_operator": return "#fd79a8";
                    case "button_clear": return "#e84393";
                    case "button_text": return "#ffffff";
                    case "button_text_operator": return "#ffffff";
                    case "copyright": return "#ffffff";
                    default: return "#ffffff";
                }
            case "forest":
                switch (element) {
                    case "background": return "#2d5a27";
                    case "display_bg": return "#3e7b3e";
                    case "display_text": return "#ffffff";
                    case "button_normal": return "#4a7c59";
                    case "button_operator": return "#81c784";
                    case "button_clear": return "#e57373";
                    case "button_text": return "#ffffff";
                    case "button_text_operator": return "#2d5a27";
                    case "copyright": return "#c8e6c9";
                    default: return "#ffffff";
                }
            case "cyberpunk":
                switch (element) {
                    case "background": return "#0f0f23";
                    case "display_bg": return "#1a1a2e";
                    case "display_text": return "#eee";
                    case "button_normal": return "#16213e";
                    case "button_operator": return "#e94560";
                    case "button_clear": return "#f39c12";
                    case "button_text": return "#eee";
                    case "button_text_operator": return "#0f0f23";
                    case "copyright": return "#bbb";
                    default: return "#eee";
                }
            case "spiderman":
                switch (element) {
                    case "background": return "#dc143c";
                    case "display_bg": return "#b22222";
                    case "display_text": return "#ffffff";
                    case "button_normal": return "#8b0000";
                    case "button_operator": return "#000080";
                    case "button_clear": return "#ff0000";
                    case "button_text": return "#ffffff";
                    case "button_text_operator": return "#ffffff";
                    case "copyright": return "#ffffff";
                    default: return "#ffffff";
                }
            case "ocean":
                switch (element) {
                    case "background": return "#006994";
                    case "display_bg": return "#004d6b";
                    case "display_text": return "#87ceeb";
                    case "button_normal": return "#4682b4";
                    case "button_operator": return "#00ced1";
                    case "button_clear": return "#1e90ff";
                    case "button_text": return "#ffffff";
                    case "button_text_operator": return "#ffffff";
                    case "copyright": return "#87ceeb";
                    default: return "#ffffff";
                }
            default: // dark
                switch (element) {
                    case "background": return "#232946";
                    case "display_bg": return "#232946";
                    case "display_text": return "#eebbc3";
                    case "button_normal": return "#232946";
                    case "button_operator": return "#eebbc3";
                    case "button_clear": return "#b8c1ec";
                    case "button_text": return "#eebbc3";
                    case "button_text_operator": return "#232946";
                    case "copyright": return "#b8c1ec";
                    case "update_text": return "#b8b8b8";
                    case "rates_text": return "#b8b8b8";
                    case "input_box_bg": return "rgba(255,255,255,0.1)";
                    default: return "#eebbc3";
                }
        }
    }

    private String getBackgroundStyle() {
        if (currentTheme.equals("dark")) {
            return "-fx-background-color: linear-gradient(to bottom right, #232946, #121629 80%);";
        }
        return "-fx-background-color: " + getThemeColor("background") + ";";
    }

    private String getDisplayStyle() {
        return String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-border-radius: 18; -fx-background-radius: 18; -fx-padding: 0 24 0 24; -fx-font-weight: bold; -fx-alignment: center-right; -fx-font-size: 72px;",
            getThemeColor("display_bg"), getThemeColor("display_text"));
    }

    private Button createMenuButton() {
        Button menuBtn = new Button("☰");
        menuBtn.setFont(Font.font("Segoe UI", 16));
        menuBtn.setPrefSize(32, 32);
        menuBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: %s; -fx-border-width: 1;",
            getThemeColor("button_operator"),
            getThemeColor("button_text_operator"),
            getThemeColor("button_operator")
        ));
        menuBtn.setOnMouseEntered(e -> menuBtn.setStyle(String.format(
            "-fx-background-color: derive(%s, -20%%); -fx-text-fill: %s; -fx-background-radius: 16; -fx-border-radius: 16; -fx-scale-x: 1.1; -fx-scale-y: 1.1;",
            getThemeColor("button_operator"),
            getThemeColor("button_text_operator")
        )));
        menuBtn.setOnMouseExited(e -> menuBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: %s; -fx-border-width: 1;",
            getThemeColor("button_operator"),  
            getThemeColor("button_text_operator"),
            getThemeColor("button_operator")
        )));
        menuBtn.setOnAction(e -> showHistoryOverlay());
        return menuBtn;
    }

    private String adjustColorBrightness(String hexColor, double factor) {
        // Simple brightness adjustment for hex colors
        try {
            Color color = Color.web(hexColor);
            double r = Math.max(0, Math.min(1, color.getRed() + factor));
            double g = Math.max(0, Math.min(1, color.getGreen() + factor));
            double b = Math.max(0, Math.min(1, color.getBlue() + factor));
            return String.format("#%02x%02x%02x", 
                (int)(r * 255), 
                (int)(g * 255), 
                (int)(b * 255));
        } catch (Exception e) {
            return hexColor; // Return original if parsing fails
        }
    }

    private VBox createHistoryOverlay() {
        // Container principal pentru scroll cu background îmbunătățit
        VBox mainContainer = new VBox();
        mainContainer.setStyle(String.format(
            "-fx-background-color: linear-gradient(to bottom, %s, derive(%s, -15%%)); -fx-background-radius: 15;", 
            getThemeColor("background"), 
            getThemeColor("background")
        ));
        
        // Content pentru scroll (cu padding redus)
        VBox scrollContent = new VBox(18);
        scrollContent.setAlignment(Pos.TOP_CENTER);
        scrollContent.setPadding(new Insets(15, 20, 25, 20));
        
        // Header compact cu X și design îmbunătățit
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_RIGHT);
        header.setPadding(new Insets(12, 20, 8, 20));
        header.setStyle(String.format(
            "-fx-background-color: linear-gradient(to bottom, %s, derive(%s, -10%%)); -fx-background-radius: 15 15 0 0;", 
            getThemeColor("background"), 
            getThemeColor("background")
        ));
        
        Button closeBtn = new Button("✕");
        closeBtn.setFont(Font.font("Segoe UI", 16));
        closeBtn.setPrefSize(32, 32);
        closeBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: %s; -fx-border-width: 1;",
            getThemeColor("button_operator"),
            getThemeColor("button_text_operator"),
            getThemeColor("button_operator")
        ));
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(String.format(
            "-fx-background-color: derive(%s, -20%%); -fx-text-fill: %s; -fx-background-radius: 16; -fx-border-radius: 16; -fx-scale-x: 1.1; -fx-scale-y: 1.1;",
            getThemeColor("button_operator"),
            getThemeColor("button_text_operator")
        )));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: %s; -fx-border-width: 1;",
            getThemeColor("button_operator"),
            getThemeColor("button_text_operator"),
            getThemeColor("button_operator")
        )));
        closeBtn.setOnAction(e -> hideHistoryOverlay());
        
        // 1. ISTORIC SECTION (sus)
        VBox historySection = createHistorySection();
        
        // 2. CONVERSIE DETALIATĂ (cu rate live)
        VBox conversionSection = createDetailedCurrencySection();
        
        // 3. SELECTARE TEME (cu cercuri colorate)
        VBox themeSection = new VBox(12);
        themeSection.setAlignment(Pos.CENTER);
        
        Label themeTitle = new Label("🎨 Selectează Tema");
        themeTitle.setFont(Font.font("Segoe UI", 16));
        themeTitle.setTextFill(Color.web(getThemeColor("button_text")));
        themeTitle.setStyle("-fx-font-weight: bold;");
        
        // Cercuri colorate pentru teme
        HBox themeCircles1 = new HBox(12);
        themeCircles1.setAlignment(Pos.CENTER);
        
        HBox themeCircles2 = new HBox(12);
        themeCircles2.setAlignment(Pos.CENTER);
        
        String[] themes = {"dark", "light", "barbie", "neon", "sunset", "forest", "cyberpunk"};
        String[] themeNames = {"Întunecat", "Luminat", "Barbie", "Neon", "Sunset", "Forest", "Cyber"};
        String[] themeColors = {"#2c3e50", "#f8f9fa", "#ff69b4", "#00ff41", "#ff6b6b", "#27ae60", "#e74c3c"};
        
        for (int i = 0; i < themes.length; i++) {
            final String theme = themes[i];
            final String themeName = themeNames[i];
            final String themeColor = themeColors[i];
            
            VBox themeOption = new VBox(5);
            themeOption.setAlignment(Pos.CENTER);
            
            // Cerc colorat
            Circle colorCircle = new Circle(18);
            colorCircle.setFill(Color.web(themeColor));
            colorCircle.setStroke(Color.WHITE);
            colorCircle.setStrokeWidth(theme.equals(currentTheme) ? 3 : 1);
            
            // Nume temă
            Label nameLabel = new Label(themeName);
            nameLabel.setFont(Font.font("Segoe UI", 9));
            nameLabel.setTextFill(Color.WHITE);
            nameLabel.setAlignment(Pos.CENTER);
            
            themeOption.getChildren().addAll(colorCircle, nameLabel);
            
            // Cursor pointer și efect hover
            themeOption.setStyle("-fx-cursor: hand;");
            themeOption.setOnMouseEntered(e -> {
                colorCircle.setStrokeWidth(3);
                colorCircle.setStroke(Color.YELLOW);
            });
            themeOption.setOnMouseExited(e -> {
                colorCircle.setStrokeWidth(theme.equals(currentTheme) ? 3 : 1);
                colorCircle.setStroke(Color.WHITE);
            });
            
            themeOption.setOnMouseClicked(e -> {
                currentTheme = theme;
                refreshTheme();
                hideHistoryOverlay();
            });
            
            if (i < 4) {
                themeCircles1.getChildren().add(themeOption);
            } else {
                themeCircles2.getChildren().add(themeOption);
            }
        }
        
        themeSection.getChildren().addAll(themeTitle, themeCircles1, themeCircles2);
        
        // Previne schimbarea fonturilor pe toate secțiunile din meniu
        historySection.setOnMouseClicked(e -> e.consume());
        conversionSection.setOnMouseClicked(e -> e.consume());
        themeSection.setOnMouseClicked(e -> e.consume());
        
        // Adaugă secțiunile în scroll content
        scrollContent.getChildren().addAll(historySection, conversionSection, themeSection);
        
        // Creează ScrollPane modern
        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle(
            "-fx-background: transparent; " +
            "-fx-background-color: transparent; " +
            "-fx-control-inner-background: transparent;" +
            "-fx-focus-color: transparent;" +
            "-fx-faint-focus-color: transparent;"
        );
        
        // Stilizare simplă pentru scrollbar (fără lookup)
        scrollPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getStylesheets().add("data:text/css," +
                    ".scroll-pane { " +
                        "-fx-background-color: transparent; " +
                    "} " +
                    ".scroll-bar:vertical { " +
                        "-fx-background-color: transparent; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 2; " +
                    "} " +
                    ".scroll-bar:vertical .thumb { " +
                        "-fx-background-color: rgba(255,255,255,0.4); " +
                        "-fx-background-radius: 8; " +
                    "} " +
                    ".scroll-bar:vertical .track { " +
                        "-fx-background-color: rgba(255,255,255,0.1); " +
                        "-fx-background-radius: 8; " +
                    "}"
                );
            }
        });
        
        header.getChildren().add(closeBtn);
        mainContainer.getChildren().addAll(header, scrollPane);
        
        // Previne complet schimbarea fonturilor când se dă click oriunde în meniu
        mainContainer.setOnMouseClicked(e -> e.consume());
        scrollPane.setOnMouseClicked(e -> e.consume());
        scrollContent.setOnMouseClicked(e -> e.consume());
        
        return mainContainer;
    }
    
    private VBox createHistorySection() {
        VBox historySection = new VBox(10);
        historySection.setAlignment(Pos.CENTER);
        
        Label historyTitle = new Label("📜 Istoric Calcule");
        historyTitle.setFont(Font.font("Segoe UI", 16));
        historyTitle.setTextFill(Color.web(getThemeColor("button_text")));
        historyTitle.setStyle("-fx-font-weight: bold;");
        
        // Box pentru istoric - cu scrollbar pentru multe calcule
        VBox historyBox = new VBox(8);
        historyBox.setAlignment(Pos.CENTER);
        historyBox.setPadding(new Insets(15));
        historyBox.setPrefHeight(200); // Înălțime fixă mai mare
        historyBox.setMaxHeight(200);
        historyBox.setMinHeight(200);
        historyBox.setStyle(String.format("-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 8; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 8;", getThemeColor("button_operator")));
        
        // ScrollPane pentru istoric
        ScrollPane historyScroll = new ScrollPane();
        historyScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        historyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        historyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        historyScroll.setPrefHeight(140);
        historyScroll.setMaxHeight(140);
        
        VBox historyList = new VBox(4);
        historyList.setAlignment(Pos.CENTER);
        historyList.setPadding(new Insets(5));
        
        if (history.isEmpty()) {
            Label noHistory = new Label("Nu există calcule în istoric");
            noHistory.setFont(Font.font("Segoe UI", 12));
            noHistory.setTextFill(Color.LIGHTGRAY);
            historyList.getChildren().add(noHistory);
        } else {
            // Afișează toate calculele din istoric (reversed order)
            for (int i = history.size() - 1; i >= 0; i--) {
                Label historyItem = new Label(history.get(i));
                historyItem.setFont(Font.font("Segoe UI", 11));
                historyItem.setTextFill(Color.WHITE);
                historyItem.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-background-radius: 4; -fx-padding: 4 8;");
                historyList.getChildren().add(historyItem);
            }
        }
        
        historyScroll.setContent(historyList);
        historyBox.getChildren().add(historyScroll);
        
        Button clearHistoryBtn = new Button("Șterge Istoric");
        clearHistoryBtn.setFont(Font.font("Segoe UI", 10));
        clearHistoryBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 5; -fx-padding: 5 10;", getThemeColor("button_clear"), getThemeColor("button_text_operator")));
        clearHistoryBtn.setOnAction(e -> {
            history.clear();
            saveHistoryToFile();
            // Refresh the history display
            historyList.getChildren().clear();
            Label noHistory = new Label("Nu există calcule în istoric");
            noHistory.setFont(Font.font("Segoe UI", 12));
            noHistory.setTextFill(Color.LIGHTGRAY);
            historyList.getChildren().add(noHistory);
        });
        
        historyBox.getChildren().add(clearHistoryBtn);
        historySection.getChildren().addAll(historyTitle, historyBox);
        return historySection;
    }
    
    private VBox createDetailedCurrencySection() {
        VBox conversionSection = new VBox(15);
        conversionSection.setAlignment(Pos.CENTER);
        
        Label conversionTitle = new Label("💱 Conversie Valutară");
        conversionTitle.setFont(Font.font("Segoe UI", 16));
        conversionTitle.setTextFill(Color.web(getThemeColor("button_text")));
        conversionTitle.setStyle("-fx-font-weight: bold;");
        
        // Data actualizării
        Label updateDate = new Label("Actualizat: " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        updateDate.setFont(Font.font("Segoe UI", 10));
        updateDate.setTextFill(Color.web(getThemeColor("update_text")));
        updateDate.setStyle("-fx-font-style: italic;");
        
        // Rate de schimb compacte pe o singură linie cu separatori
        Label ratesLabel = new Label(String.format(
            "1EUR=%.2fRON • 1USD=%.2fRON • 1GBP=%.2fRON • 1TRY=%.3fRON", 
            exchangeRates.getOrDefault("RON", 4.97),
            exchangeRates.getOrDefault("RON", 4.97) / exchangeRates.getOrDefault("USD", 1.09),
            exchangeRates.getOrDefault("RON", 4.97) / exchangeRates.getOrDefault("GBP", 0.85),
            exchangeRates.getOrDefault("RON", 4.97) / exchangeRates.getOrDefault("TRY", 37.15)
        ));
        ratesLabel.setFont(Font.font("Segoe UI", 11));
        ratesLabel.setTextFill(Color.web(getThemeColor("rates_text")));
        ratesLabel.setAlignment(Pos.CENTER);
        
        // Input pentru conversie (preîncărcat cu suma din calculator)
        VBox inputBox = new VBox(10);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setPadding(new Insets(15));
        inputBox.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 8; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 8;", getThemeColor("input_box_bg"), getThemeColor("button_operator")));
        
        TextField amountInput = new TextField();
        amountInput.setPromptText("Introdu suma pentru conversie...");
        amountInput.setPrefWidth(200);
        amountInput.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 6; -fx-padding: 8;", getThemeColor("button_normal"), getThemeColor("button_text")));
        
        // Preîncarcă cu suma din calculator dacă există
        String currentDisplayText = display.getText().trim();
        if (!currentDisplayText.equals("0") && !currentDisplayText.isEmpty()) {
            try {
                Double.parseDouble(currentDisplayText); // Verifică dacă e număr valid
                amountInput.setText(currentDisplayText);
            } catch (NumberFormatException e) {
                // Nu e număr valid, lasă gol
            }
        }
        
        // Conversie interactivă cu RON->EUR ca default
        HBox conversionBox = new HBox(15);
        conversionBox.setAlignment(Pos.CENTER);
        
        // Label pentru rezultat (declarat mai întâi)
        Label resultLabel = new Label("Introdu o sumă pentru conversie");
        resultLabel.setTextFill(Color.web(currentTheme.equals("light") ? "#2c3e50" : "#ffffff"));
        resultLabel.setFont(Font.font("Segoe UI", 12));
        resultLabel.setAlignment(Pos.CENTER);
        resultLabel.setWrapText(true);
        resultLabel.setMaxWidth(250);
        String bgColor = currentTheme.equals("light") ? "#adb5bd" : "rgba(0,0,0,0.4)";
        resultLabel.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6; -fx-padding: 10; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 6;", bgColor, getThemeColor("button_operator")));
        
        ComboBox<String> fromCurrency = new ComboBox<>();
        fromCurrency.getItems().addAll("RON", "EUR", "USD", "GBP", "TRY");
        fromCurrency.setValue("RON"); // Default RON
        selectedFromCurrency = "RON";
        fromCurrency.setPrefWidth(70);
        
        // Stilizare originală cu text negru pentru vizibilitate
        fromCurrency.setButtonCell(new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(item);
                    setTextFill(Color.BLACK);
                }
            }
        });
        
        fromCurrency.setOnAction(e -> {
            selectedFromCurrency = fromCurrency.getValue();
            if (!amountInput.getText().trim().isEmpty()) {
                convertAndDisplay(amountInput, resultLabel);
            }
        });
        
        Label arrowLabel = new Label("→");
        arrowLabel.setTextFill(Color.WHITE);
        arrowLabel.setFont(Font.font("Segoe UI", 16));
        
        ComboBox<String> toCurrency = new ComboBox<>();
        toCurrency.getItems().addAll("RON", "EUR", "USD", "GBP", "TRY");
        toCurrency.setValue("EUR"); // Default EUR
        selectedToCurrency = "EUR";
        toCurrency.setPrefWidth(70);
        
        // Stilizare originală cu text negru pentru vizibilitate
        toCurrency.setButtonCell(new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(item);
                    setTextFill(Color.BLACK);
                }
            }
        });
        
        toCurrency.setOnAction(e -> {
            selectedToCurrency = toCurrency.getValue();
            if (!amountInput.getText().trim().isEmpty()) {
                convertAndDisplay(amountInput, resultLabel);
            }
        });
        
        conversionBox.getChildren().addAll(fromCurrency, arrowLabel, toCurrency);
        
        // Butoane pentru acțiuni
        HBox actionButtons = new HBox(10);
        actionButtons.setAlignment(Pos.CENTER);
        
        Button useCalculatorBtn = new Button("Actualizează din Calcul");
        useCalculatorBtn.setFont(Font.font("Segoe UI", 11));
        useCalculatorBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 6; -fx-padding: 8 15;", getThemeColor("button_operator"), getThemeColor("button_text_operator")));
        useCalculatorBtn.setOnAction(e -> {
            String newAmount = display.getText().trim();
            if (!newAmount.equals("0") && !newAmount.isEmpty()) {
                try {
                    Double.parseDouble(newAmount); // Verifică dacă e număr valid
                    amountInput.setText(newAmount);
                    convertAndDisplay(amountInput, resultLabel);
                } catch (NumberFormatException ex) {
                    resultLabel.setText("Rezultatul din calculator nu este un număr valid");
                }
            } else {
                resultLabel.setText("Nu există rezultat în calculator pentru conversie");
            }
        });
        
        Button convertBtn = new Button("Convertește Manual");
        convertBtn.setFont(Font.font("Segoe UI", 11));
        convertBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 6; -fx-padding: 8 15;", getThemeColor("button_operator"), getThemeColor("button_text_operator")));
        convertBtn.setOnAction(e -> convertAndDisplay(amountInput, resultLabel));
        
        actionButtons.getChildren().addAll(useCalculatorBtn, convertBtn);
        
        inputBox.getChildren().addAll(amountInput, conversionBox, actionButtons, resultLabel);
        
        // Conversie automată la început dacă există sumă
        if (!amountInput.getText().trim().isEmpty()) {
            convertAndDisplay(amountInput, resultLabel);
        }
        
        conversionSection.getChildren().addAll(conversionTitle, updateDate, ratesLabel, inputBox);
        return conversionSection;
    }
    
    private void convertAndDisplay(TextField amountInput, Label resultLabel) {
        try {
            String amountText = amountInput.getText().trim();
            if (amountText.isEmpty()) {
                resultLabel.setText("Introdu o sumă pentru conversie");
                return;
            }
            double amount = Double.parseDouble(amountText);
            double converted = convertCurrency(amount, selectedFromCurrency, selectedToCurrency);
            resultLabel.setText(String.format("%.2f %s = %.2f %s", amount, selectedFromCurrency, converted, selectedToCurrency));
        } catch (NumberFormatException ex) {
            resultLabel.setText("Sumă invalidă pentru conversie");
        } catch (Exception ex) {
            resultLabel.setText("Eroare la conversie: " + ex.getMessage());
        }
    }

    private void showHistoryOverlay() {
        // Recreate overlay to reflect current theme
        historyOverlay = createHistoryOverlay();
        
        // Replace the overlay in root
        StackPane root = (StackPane) mainRoot.getParent();
        if (root.getChildren().size() > 1) {
            root.getChildren().remove(1);
        }
        root.getChildren().add(historyOverlay);
        historyOverlay.setVisible(true);
        
        // Update conversion after overlay is created
        convertCurrentResult();
    }

    private void hideHistoryOverlay() {
        historyOverlay.setVisible(false);
    }

    private void changeTheme(String theme) {
        currentTheme = theme;
        refreshTheme();
    }

    private void refreshTheme() {
        // Refresh display
        display.setStyle(getDisplayStyle());
        
        // Refresh background
        mainRoot.setStyle(getBackgroundStyle());
        
        // Refresh buttons
        refreshButtons();
        
        // Refresh copyright
        Label copyright = (Label) mainRoot.getBottom();
        copyright.setTextFill(Color.web(getThemeColor("copyright")));
    }

    private void refreshButtons() {
        String[][] btns = {
            {"C", "⌫", "%", "/"},
            {"7", "8", "9", "*"},
            {"4", "5", "6", "-"},
            {"1", "2", "3", "+"},
            {"0", "", ".", "="}
        };
        
        int btnIndex = 0;
        for (int r = 0; r < btns.length; r++) {
            for (int c = 0; c < btns[r].length; c++) {
                String txt = btns[r][c];
                if (txt.isEmpty()) continue;
                
                Button btn = (Button) buttonGrid.getChildren().get(btnIndex);
                btn.setStyle(getButtonStyle(txt, false));
                btnIndex++;
            }
        }
    }

    private void setupKeyboardSupport(Scene scene) {
        scene.setOnKeyPressed(e -> {
            // Handle Shift + digit combinations for operators
            if (e.isShiftDown()) {
                switch (e.getCode()) {
                    case DIGIT1: handleButton("!"); break; // Shift+1 = !
                    case DIGIT3: handleButton("#"); break; // Shift+3 = #
                    case DIGIT4: handleButton("$"); break; // Shift+4 = $
                    case DIGIT5: handleButton("%"); break; // Shift+5 = %
                    case DIGIT6: handleButton("^"); break; // Shift+6 = ^
                    case DIGIT7: handleButton("&"); break; // Shift+7 = &
                    case DIGIT8: handleButton("*"); break; // Shift+8 = *
                    case DIGIT9: handleButton("("); break; // Shift+9 = (
                    case DIGIT0: handleButton(")"); break; // Shift+0 = )
                    case EQUALS: handleButton("+"); break; // Shift+= = +
                    case MINUS: handleButton("_"); break; // Shift+- = _
                    default: break;
                }
                return;
            }
            
            // Normal key handling
            switch (e.getCode()) {
                case DIGIT0: case NUMPAD0: handleButton("0"); break;
                case DIGIT1: case NUMPAD1: handleButton("1"); break;
                case DIGIT2: case NUMPAD2: handleButton("2"); break;
                case DIGIT3: case NUMPAD3: handleButton("3"); break;
                case DIGIT4: case NUMPAD4: handleButton("4"); break;
                case DIGIT5: case NUMPAD5: handleButton("5"); break;
                case DIGIT6: case NUMPAD6: handleButton("6"); break;
                case DIGIT7: case NUMPAD7: handleButton("7"); break;
                case DIGIT8: case NUMPAD8: handleButton("8"); break;
                case DIGIT9: case NUMPAD9: handleButton("9"); break;
                case ADD: case PLUS: handleButton("+"); break;
                case SUBTRACT: case MINUS: handleButton("-"); break;
                case MULTIPLY: handleButton("*"); break;
                case DIVIDE: case SLASH: handleButton("/"); break;
                case ENTER: case EQUALS: handleButton("="); break;
                case BACK_SPACE: handleButton("⌫"); break;
                case DECIMAL: case PERIOD: handleButton("."); break;
                case C: handleButton("C"); break;
                case ESCAPE: hideHistoryOverlay(); break;
                default:
                    String ch = e.getText();
                    if (ch.matches("[0-9+\\-*/=cC.%]")) handleButton(ch);
            }
        });
    }
    private GridPane createButtons() {
        String[][] btns = {
            {"C", "⌫", "%", "/"},
            {"7", "8", "9", "*"},
            {"4", "5", "6", "-"},
            {"1", "2", "3", "+"},
            {"0", "", ".", "="}
        };
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints(80);
            grid.getColumnConstraints().add(col);
        }
        for (int r = 0; r < btns.length; r++) {
            for (int c = 0; c < btns[r].length; c++) {
                String txt = btns[r][c];
                if (txt.isEmpty()) continue;
                Button btn = new Button(txt);
                btn.setFont(Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 32));
                btn.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                if (r == 4 && c == 0) {
                    btn.setPrefSize(172, 64);
                    btn.setMinSize(172, 64);
                    btn.setMaxSize(172, 64);
                    GridPane.setColumnSpan(btn, 2);
                } else {
                    btn.setPrefSize(80, 64);
                    btn.setMinSize(80, 64);
                    btn.setMaxSize(80, 64);
                }
                btn.setStyle(getButtonStyle(txt, false));
                btn.setOnMouseEntered(e -> btn.setStyle(getButtonStyle(txt, true)));
                btn.setOnMouseExited(e -> {
                    boolean isActive = (lastOperatorButton == btn && "+-*/".contains(txt));
                    btn.setStyle(getButtonStyle(txt, isActive));
                });
                btn.setOnAction(e -> {
                    handleButton(txt);
                });
                grid.add(btn, c, r);
            }
        }
        return grid;
    }

    private String getButtonStyle(String txt, boolean isPressed) {
        String base = "-fx-background-radius: 32; -fx-border-radius: 32; -fx-font-weight: bold; -fx-padding: 0; -fx-font-size: 32px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);";
        String hoverEffect = "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 4); -fx-scale-x: 1.05; -fx-scale-y: 1.05;";
        String color;
        
        if ("C".equals(txt) || "⌫".equals(txt)) {
            if (isPressed) {
                // La hover, butoanele clear schimba cu culoarea operator
                color = String.format("-fx-background-color: %s; -fx-text-fill: white;", getThemeColor("button_operator"));
                base = base.replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);", hoverEffect);
            } else {
                color = String.format("-fx-background-color: %s; -fx-text-fill: %s;", getThemeColor("button_clear"), getThemeColor("button_text_operator"));
            }
        } else if ("=+-*/%".contains(txt)) {
            if (isPressed) {
                if ("=+-*/".contains(txt) && !txt.equals("=")) {
                    // Butoanele operator la hover schimba cu culoarea clear
                    color = String.format("-fx-background-color: %s; -fx-text-fill: white;", getThemeColor("button_clear"));
                } else if (txt.equals("=")) {
                    // Butonul equals ramane cu culoarea sa
                    color = String.format("-fx-background-color: %s; -fx-text-fill: %s;", getThemeColor("button_operator"), getThemeColor("button_text_operator"));
                } else {
                    color = String.format("-fx-background-color: %s; -fx-text-fill: %s;", getThemeColor("button_text"), getThemeColor("button_operator"));
                }
                base = base.replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);", hoverEffect);
            } else {
                color = String.format("-fx-background-color: %s; -fx-text-fill: %s;", getThemeColor("button_operator"), getThemeColor("button_text_operator"));
            }
        } else {
            if (isPressed) {
                color = String.format("-fx-background-color: %s; -fx-text-fill: %s;", getThemeColor("button_operator"), getThemeColor("button_text"));
                base = base.replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);", hoverEffect);
            } else {
                color = String.format("-fx-background-color: %s; -fx-text-fill: %s;", getThemeColor("button_normal"), getThemeColor("button_text"));
            }
        }
        return color + base;
    }

    private void handleButton(String txt) {
        if (isEmptyInput && !txt.equals("C") && !txt.equals("⌫")) {
            display.setText("");
            display.setFont(Font.font("Segoe UI", 72));
            display.positionCaret(0);
            isEmptyInput = false;
        }
        
        // Reset operator button state
        if (lastOperatorButton != null && !"+-*/".contains(txt)) {
            resetOperatorButton();
        }
        
        switch (txt) {
            case "C":
                currentInput = "";
                display.setText("0");
                display.setFont(Font.font("Segoe UI", 72));
                display.positionCaret(0);
                justCalculated = false;
                lastWasEqual = false;
                isEmptyInput = true;
                resetOperatorButton();
                break;
            case ".":
                if (currentInput.isEmpty()) {
                    currentInput = "0.";
                    display.setText(currentInput);
                    display.setFont(Font.font("Segoe UI", 72));
                    display.positionCaret(currentInput.length());
                } else if (!lastChar(currentInput).equals(".")) {
                    // nu permite mai multe puncte consecutive
                    // nu permite punct după operator
                    if (isOperator(lastChar(currentInput))) {
                        currentInput += "0.";
                        display.setText(currentInput);
                        display.setFont(Font.font("Segoe UI", 72));
                        display.positionCaret(currentInput.length());
                    } else if (!currentInput.contains(".") || 
                              (currentInput.contains(".") && currentInput.lastIndexOf('.') < currentInput.lastIndexOf('+') ||
                               currentInput.lastIndexOf('.') < currentInput.lastIndexOf('-') ||
                               currentInput.lastIndexOf('.') < currentInput.lastIndexOf('*') ||
                               currentInput.lastIndexOf('.') < currentInput.lastIndexOf('/'))) {
                        currentInput += ".";
                        display.setText(currentInput);
                        display.setFont(Font.font("Segoe UI", 72));
                        display.positionCaret(currentInput.length());
                    }
                }
                justCalculated = false;
                break;
            case "⌫":
                if (!currentInput.isEmpty()) {
                    currentInput = currentInput.substring(0, currentInput.length() - 1);
                    if (currentInput.isEmpty()) {
                        display.setText("0");
                        isEmptyInput = true;
                    } else {
                        display.setText(currentInput);
                    }
                    display.setFont(Font.font("Segoe UI", 72));
                    display.positionCaret(currentInput.length());
                }
                break;
            case "%":
                if (!currentInput.isEmpty() && !isOperator(lastChar(currentInput))) {
                    currentInput += "%";
                    display.setText(currentInput);
                    display.setFont(Font.font("Segoe UI", 72));
                    display.positionCaret(currentInput.length());
                }
                break;
            case "+":
            case "-":
            case "*":
            case "/":
                if (currentInput.isEmpty() || isOperator(lastChar(currentInput))) break;
                if (lastWasEqual) lastWasEqual = false;
                currentInput += txt;
                display.setText(currentInput);
                display.setFont(Font.font("Segoe UI", 72));
                display.positionCaret(currentInput.length());
                justCalculated = false;
                
                // Set active operator button
                setActiveOperatorButton(txt);
                break;
            case "=":
                if (currentInput.isEmpty() || isOperator(lastChar(currentInput))) break;
                String equation = currentInput;
                double result = evalLeftToRight(currentInput.replace("%", "/100"));
                String resultStr = (result == (long) result) ? String.valueOf((long) result) : String.valueOf(result);
                
                // Add to history
                history.add(equation + " = " + resultStr);
                saveHistory(); // Save to file immediately
                
                display.setText(resultStr);
                display.setFont(Font.font("Segoe UI", 72));
                display.positionCaret(0);
                currentInput = resultStr;
                justCalculated = true;
                lastWasEqual = true;
                resetOperatorButton();
                break;
            default:
                if (lastWasEqual) {
                    currentInput = "";
                    lastWasEqual = false;
                }
                currentInput += txt;
                display.setText(currentInput);
                display.setFont(Font.font("Segoe UI", 72));
                display.positionCaret(currentInput.length());
                justCalculated = false;
        }
    }
    
    private void setActiveOperatorButton(String operator) {
        resetOperatorButton();
        
        // Find and highlight the operator button
        String[][] btns = {
            {"C", "⌫", "%", "/"},
            {"7", "8", "9", "*"},
            {"4", "5", "6", "-"},
            {"1", "2", "3", "+"},
            {"0", "", ".", "="}
        };
        
        int btnIndex = 0;
        for (int r = 0; r < btns.length; r++) {
            for (int c = 0; c < btns[r].length; c++) {
                String txt = btns[r][c];
                if (txt.isEmpty()) continue;
                
                if (txt.equals(operator)) {
                    Button btn = (Button) buttonGrid.getChildren().get(btnIndex);
                    btn.setStyle(getButtonStyle(txt, true));
                    lastOperatorButton = btn;
                    break;
                }
                btnIndex++;
            }
        }
    }
    
    private void resetOperatorButton() {
        if (lastOperatorButton != null) {
            String txt = lastOperatorButton.getText();
            lastOperatorButton.setStyle(getButtonStyle(txt, false));
            lastOperatorButton = null;
        }
    }

    // Evaluare stânga-dreapta fără prioritate operatori
    private double evalLeftToRight(String expr) {
        expr = expr.replaceAll("\\s", "");
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?|[+*/-])");
        java.util.regex.Matcher m = p.matcher(expr);
        java.util.List<String> tokens = new java.util.ArrayList<>();
        while (m.find()) tokens.add(m.group());
        if (tokens.isEmpty()) return 0;
        double result = Double.parseDouble(tokens.get(0));
        for (int i = 1; i < tokens.size(); i += 2) {
            if (i + 1 >= tokens.size()) break;
            String op = tokens.get(i);
            double val = Double.parseDouble(tokens.get(i + 1));
            switch (op) {
                case "+": result += val; break;
                case "-": result -= val; break;
                case "*": result *= val; break;
                case "/": result /= val; break;
            }
        }
        return result;
    }

    private boolean isOperator(String txt) {
        return txt.equals("+") || txt.equals("-") || txt.equals("*") || txt.equals("/");
    }

    private String lastChar(String s) {
        if (s.isEmpty()) return "";
        return s.substring(s.length()-1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
