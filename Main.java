import syntaxtree.*;
import visitor.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import helpers.ClassData;
import helpers.VarData;
import helpers.MethodData;

class Main {
    public static void main (String [] args){
    	if(args.length < 1){
    	    System.err.println("Usage: java Driver <inputFile1> <inputFile2> ...");
    	    System.exit(1);
    	}
    	FileInputStream fis = null;
        for (String arg : args) {
            try{
                System.out.println("\n==== Parsing "+ arg + " ====");
        	    fis = new FileInputStream(arg);
        	    MiniJavaParser parser = new MiniJavaParser(fis);
        	    System.err.println("==== Program parsed successfully ====");

                System.out.println("==== Type Checking "+ arg + " ====\n");
        	    SymbolVisitor symbol_visitor = new SymbolVisitor();
        	    Goal root = parser.Goal();
        	    System.out.println(root.accept(symbol_visitor));
                // System.out.println("\nPrinting Offsets:");
                // symbol_visitor.printOffsets();

                // TypeCheckerVisitor type_checker = new TypeCheckerVisitor(symbol_visitor.getSymbolTable());
                // root.accept(type_checker);
                // System.out.println("\n==== Type Checking completed ====");

                System.out.println("\n==== Generating LLVM code for "+ arg +" ====");
                LLVMVisitor code_gernerator = new LLVMVisitor(symbol_visitor.getSymbolTable());
                root.accept(code_gernerator,null);
                System.out.println(code_gernerator.getCode());
                System.out.println("\n==== Code generation completed ====");
                BufferedWriter writer = new BufferedWriter(new FileWriter(arg+".ll"));
                writer.write(code_gernerator.getCode());
                writer.close();

        	}
        	catch(ParseException ex){
        	    System.out.println(ex.getMessage());
        	}
        	catch(FileNotFoundException ex){
        	    System.err.println(ex.getMessage());
        	}
            catch(IllegalStateException ex){
                System.err.println(ex.getMessage());
            }
            catch(IOException ex){
                System.err.println(ex.getMessage());
            }
        	finally{
        	    try{
        		          if(fis != null) fis.close();
        	    }
        	    catch(IOException ex){
        		          System.err.println(ex.getMessage());
        	    }
        	}
        }

    }
}
