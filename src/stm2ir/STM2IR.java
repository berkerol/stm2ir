package stm2ir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Scanner;
import java.util.TreeSet;

public class STM2IR {

    /**
     * Stores variables of LLVM.
     */
    private static final TreeSet<String> VARIABLES = new TreeSet<>();
    /**
     * Counter for input file lines.
     */
    private static int lineCounter = 0;
    /**
     * Stores the strings to be written to the output file.
     */
    private static final LinkedList<String> LIST = new LinkedList<>();
    /**
     * Counter for temporary variables of LLVM.
     */
    private static int tempCounter = 0;

    /**
     * Read lines from input file, processes them and prints the LLVM code to
     * output file.
     *
     * @param args path for input file
     * @throws FileNotFoundException if input file is not found
     * @throws UnsupportedEncodingException if the named charset is not
     * supported
     */
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        LIST.add("; ModuleID = 'stm2ir'");
        LIST.add("declare i32 @printf(i8*, ...)");
        LIST.add("@print.str = constant [4 x i8] c\"%d\\0A\\00\"");
        LIST.add("define i32 @main() {");
        Scanner input = new Scanner(new File(args[0]), "UTF-8");
        while (input.hasNextLine()) {
            lineCounter++;
            String line = input.nextLine().replaceAll("\\s+", "");
            if (line.contains("=")) {
                String left = line.split("=")[0], right = line.split("=")[1];
                if (!VARIABLES.contains(left)) {
                    VARIABLES.add(left);
                    LIST.add("%" + left + " = alloca i32");
                }
                LIST.add("store i32 " + findVariable(expression(right)) + ", i32* %" + left);
            }
            else {
                LIST.add("call i32 (i8*, ...)* @printf(i8* getelementptr ([4 x i8]* @print.str, i32 0, i32 0), i32 " + findVariable(expression(line)) + " )");
                tempCounter++;
            }
        }
        input.close();
        LIST.add("ret i32 0");
        LIST.add("}");
        PrintStream printStream = new PrintStream(args[0].split("\\.")[0] + ".ll", "UTF-8");
        for (ListIterator<String> iterator = LIST.listIterator(); iterator.hasNext();) {
            printStream.println(iterator.next());
        }
        printStream.close();
    }

    /**
     * Checks whether all parenthesis are matching.
     *
     * @param index return value from indexOf("(") or indexOf(")")
     * @param type left or right parenthesis
     */
    private static void errorMissingParanthesis(int index, String type) {
        if (index < 0) {
            System.out.println("Error : Line " + lineCounter + ": " + type + " paranthesis is missing.");
            System.exit(2);
        }
    }

    /**
     * Checks whether there is two numbers or variables around the operators.
     *
     * @param expression string containing variables and operators
     * @param index variable's supposed starting or ending point
     * @param operator operator type
     */
    private static void errorMissingVariable(String expression, int index, String operator) {
        if (index >= expression.length() || index < 0 || expression.charAt(index) == '+' || expression.charAt(index) == '-'
                || expression.charAt(index) == '*' || expression.charAt(index) == '/' || expression.charAt(index) == '(' || expression.charAt(index) == ')') {
            System.out.println("Error: Line " + lineCounter + ": missing variable near " + operator + ".");
            System.exit(3);
        }
    }

    /**
     * Processes all operations for all operators in a given string. First
     * removes all parenthesis then processes the operations according to the
     * operator precedence. All operations of a type are finished before the
     * next operation type is executed.
     *
     * @param expression string containing variables and operators
     * @return processed string
     */
    private static String expression(String expression) {
        while (expression.contains("(") || expression.contains(")")) {
            int begin = expression.lastIndexOf('('), end = expression.indexOf(')', begin);
            errorMissingParanthesis(begin, "left");
            errorMissingParanthesis(end, "right");
            String inside = expression.substring(begin + 1, end);
            expression = expression.replace("(" + inside + ")", expression(inside));
        }
        expression = operation(expression, "*", "mul");
        expression = operation(expression, "/", "sdiv");
        expression = operation(expression, "-", "sub");
        expression = operation(expression, "+", "add");
        return expression;
    }

    /**
     * Checks whether given string is a number or variable. If it is a number
     * then returns it. If it is a variable first checks for errors then returns
     * it as a temp variable.
     *
     * @param expression string consisting of number or temp variable
     * @return number of temp variable of LLVM
     */
    private static String findVariable(String expression) {
        try {
            Integer.parseInt(expression);
        }
        catch (NumberFormatException ex) {
            if (VARIABLES.contains(expression)) {
                if (expression.charAt(0) != '%') {
                    String original = expression;
                    expression = "%" + ++tempCounter;
                    VARIABLES.add(expression);
                    LIST.add(expression + " = load i32* %" + original);
                }
            }
            else {
                System.out.println("Error: Line " + lineCounter + ": undefined variable " + expression + ".");
                System.exit(1);
            }
        }
        return expression;
    }

    /**
     * Processes all operations for a given operator in a given string. First
     * extracts the left and right operands then checks for errors then does the
     * operation and changes the string for this operation then returns this
     * string.
     *
     * @param expression string containing variables and operators
     * @param operator operator type
     * @param type LLVM code of operation
     * @return processed string
     */
    private static String operation(String expression, String operator, String type) {
        while (expression.contains(operator)) {
            int leftEnd = expression.indexOf(operator) - 1, leftBegin = leftEnd, rightBegin = expression.indexOf(operator) + 1, rightEnd = rightBegin;
            while (true) {
                if (leftBegin - 1 < 0) {
                    break;
                }
                if (expression.charAt(leftBegin - 1) == '+' || expression.charAt(leftBegin - 1) == '-' || expression.charAt(leftBegin - 1) == '*' || expression.charAt(leftBegin - 1) == '/') {
                    break;
                }
                leftBegin--;
            }
            errorMissingVariable(expression, leftBegin, operator);
            errorMissingVariable(expression, leftEnd, operator);
            while (true) {
                if (rightEnd + 1 >= expression.length()) {
                    break;
                }
                if (expression.charAt(rightEnd + 1) == '+' || expression.charAt(rightEnd + 1) == '-' || expression.charAt(rightEnd + 1) == '*' || expression.charAt(rightEnd + 1) == '/') {
                    break;
                }
                rightEnd++;
            }
            errorMissingVariable(expression, rightBegin, operator);
            errorMissingVariable(expression, rightEnd, operator);
            String left = expression.substring(leftBegin, leftEnd + 1), right = expression.substring(rightBegin, rightEnd + 1);
            String tempLeft = findVariable(left), tempRight = findVariable(right), temp = "%" + ++tempCounter;
            VARIABLES.add(temp);
            LIST.add(temp + " = " + type + " i32 " + tempLeft + "," + tempRight);
            expression = expression.replace(left + operator + right, temp);
        }
        return expression;
    }
}
