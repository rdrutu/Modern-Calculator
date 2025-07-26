public class CalculatorLogic {
    private String currentInput = "";
    private boolean justCalculated = false;
    private boolean lastWasEqual = false;
    
    public String getCurrentInput() {
        return currentInput;
    }
    
    public void setCurrentInput(String input) {
        currentInput = input;
    }
    
    public boolean isJustCalculated() {
        return justCalculated;
    }
    
    public void setJustCalculated(boolean calculated) {
        justCalculated = calculated;
    }
    
    public boolean isLastWasEqual() {
        return lastWasEqual;
    }
    
    public void setLastWasEqual(boolean wasEqual) {
        lastWasEqual = wasEqual;
    }
    
    public void reset() {
        currentInput = "";
        justCalculated = false;
        lastWasEqual = false;
    }
    
    public boolean isOperator(String txt) {
        return txt.equals("+") || txt.equals("-") || txt.equals("*") || txt.equals("/");
    }
    
    public String getLastChar(String s) {
        if (s.isEmpty()) return "";
        return s.substring(s.length()-1);
    }
    
    public double evaluateLeftToRight(String expr) {
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
    
    public String handleBackspace() {
        if (!currentInput.isEmpty()) {
            currentInput = currentInput.substring(0, currentInput.length() - 1);
            if (currentInput.isEmpty()) {
                return "0";
            } else {
                return currentInput;
            }
        }
        return "0";
    }
}
