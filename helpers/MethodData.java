package helpers;
import syntaxtree.*;
import java.util.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class MethodData {
    String type;
    String name;
    ArrayList<VarData> args;
    ArrayList<VarData> var_declarations;
    int offset;


    public MethodData(String t, String n, int offset){
        this.type = t;
        this.name = n;
        this.offset = offset;
        this.args = new ArrayList<VarData>();
        this.var_declarations = new ArrayList<VarData>();
    }

    public void addArg(VarData argv){
        this.args.add(argv);
    }

    public void addVar(VarData var){
        this.var_declarations.add(var);
    }

    public ArrayList<VarData> getVars(){
        return this.var_declarations;
    }

    public ArrayList<VarData> getArgs(){
        return this.args;
    }

    public String getType(){
        return this.type;
    }

    public String getName(){
        return this.name;
    }

    public int getOffset(){
        return this.offset;
    }
}
